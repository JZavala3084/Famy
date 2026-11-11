import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Client, IFrame, IMessage } from '@stomp/stompjs';
import { Subject, firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { AppConfig } from './config';
import { FamilyService } from './family.service';
import { ChangeEvent, ChangePage } from './models';

export type LiveStatus = 'offline' | 'connecting' | 'live';

/** Códigos de cierre del WebSocket definidos por el backend. */
const CLOSE_TOKEN_EXPIRED = 4401;
const CLOSE_REMOVED = 4403;
const FEED_SIZE = 30;
const SEEN_LIMIT = 2_000;

/**
 * Tiempo real (backend/README.md § Tiempo real):
 * 1. Guarda el cursor (`GET /changes/cursor`) antes de que las pantallas carguen por REST.
 * 2. Conecta STOMP a `/ws` con el access token en el frame CONNECT y se suscribe a la familia.
 * 3. Al (re)conectar, se pone al día con `GET /changes?since=` y después aplica los eventos en vivo.
 * 4. Si el cursor ya se purgó (410), emite `resync` para que cada pantalla recargue todo.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly auth = inject(AuthService);
  private readonly family = inject(FamilyService);
  private readonly router = inject(Router);

  private client: Client | null = null;
  private familyId: string | null = null;
  private cursor = 0;
  /** Cambios anteriores a la carga inicial (ya incluidos en los datos REST). */
  private baseline = 0;
  /** Ids ya aplicados: el orden de llegada no está garantizado, así que se deduplica por id y no por cursor. */
  private seen = new Set<number>();
  private catchingUp = false;
  private buffer: ChangeEvent[] = [];
  private forceRefresh = false;

  readonly status = signal<LiveStatus>('offline');
  /** Cambios aplicables (sin duplicados, en orden). */
  readonly events = new Subject<ChangeEvent>();
  /** Se emite cuando hay que recargar todo desde REST. */
  readonly resync = new Subject<void>();
  /** Actividad reciente de la familia (más nuevo primero). */
  readonly feed = signal<ChangeEvent[]>([]);

  /** Se resuelve cuando ya se guardó el cursor: las pantallas esperan esto antes de cargar por REST. */
  ready: Promise<void> = Promise.resolve();

  start(familyId: string): Promise<void> {
    if (this.familyId === familyId) return this.ready;
    this.stop();
    this.familyId = familyId;
    this.status.set('connecting');
    this.ready = this.loadRecentActivity(familyId).then(
      () => {
        if (this.familyId === familyId) this.connect(familyId);
      },
      () => {
        // Sin servidor no hay cursor: reintentar más tarde sin bloquear a las pantallas.
        this.status.set('offline');
        setTimeout(() => {
          if (this.familyId === familyId && !this.client) {
            this.familyId = null;
            this.start(familyId);
          }
        }, 5_000);
      },
    );
    return this.ready;
  }

  private connect(familyId: string): void {
    const client = new Client({
      brokerURL: this.config.wsUrl(),
      heartbeatIncoming: 20_000,
      heartbeatOutgoing: 20_000,
      reconnectDelay: 3_000,
      beforeConnect: async (c) => {
        const token = this.forceRefresh ? await this.auth.refresh() : await this.auth.freshToken();
        this.forceRefresh = false;
        if (!token) {
          await c.deactivate();
          this.status.set('offline');
          return;
        }
        c.connectHeaders = { Authorization: `Bearer ${token}` };
        this.status.set('connecting');
      },
      onConnect: () => this.onConnect(client, familyId),
      onStompError: (frame: IFrame) => {
        console.warn('STOMP error', frame.headers['message']);
      },
      onWebSocketClose: (evt: CloseEvent) => {
        if (this.client !== client) return;
        this.status.set('connecting');
        if (evt.code === CLOSE_TOKEN_EXPIRED) {
          this.forceRefresh = true;
        } else if (evt.code === CLOSE_REMOVED) {
          this.stop();
          this.family.load().finally(() => this.router.navigate(['/bienvenida']));
        }
      },
    });
    this.client = client;
    client.activate();
  }

  stop(): void {
    const c = this.client;
    this.client = null;
    this.familyId = null;
    this.buffer = [];
    this.feed.set([]);
    this.status.set('offline');
    c?.deactivate();
  }

  private onConnect(client: Client, familyId: string): void {
    this.catchingUp = true;
    this.buffer = [];
    client.subscribe(`/topic/families/${familyId}`, (msg: IMessage) => {
      const evt = JSON.parse(msg.body) as ChangeEvent;
      if (this.catchingUp) {
        this.buffer.push(evt);
      } else {
        this.accept(evt);
      }
    });
    this.catchUp(familyId)
      .then(() => {
        if (this.client !== client) return;
        this.catchingUp = false;
        const pending = this.buffer.sort((a, b) => a.id - b.id);
        this.buffer = [];
        pending.forEach((e) => this.accept(e));
        this.status.set('live');
      })
      .catch(() => {
        this.catchingUp = false;
        this.status.set('connecting');
      });
  }

  /** Cursor actual del servidor; las pantallas lo piden antes de su carga inicial. */
  private async catchUp(familyId: string): Promise<void> {
    try {
      let page: ChangePage;
      do {
        page = await firstValueFrom(
          this.http.get<ChangePage>(this.config.api(`/families/${familyId}/changes`), {
            params: { since: this.cursor, limit: 500 },
          }),
        );
        page.changes.forEach((e) => this.accept(e));
        this.cursor = Math.max(this.cursor, page.cursor);
      } while (page.hasMore);
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 410) {
        this.cursor = await this.fetchCursor(familyId);
        this.baseline = this.cursor;
        this.seen.clear();
        this.resync.next();
        return;
      }
      throw err;
    }
  }

  private accept(evt: ChangeEvent): void {
    if (evt.familyId !== this.familyId || evt.id <= this.baseline || this.seen.has(evt.id)) return;
    this.seen.add(evt.id);
    if (this.seen.size > SEEN_LIMIT) {
      // Set conserva el orden de inserción: se olvidan los más viejos.
      const old = this.seen.values().next().value!;
      this.seen.delete(old);
      this.baseline = Math.max(this.baseline, old);
    }
    this.cursor = Math.max(this.cursor, evt.id);
    if (evt.entity === 'family.member') {
      this.family.reloadCurrent().catch(() => undefined);
    }
    this.feed.update((f) => [evt, ...f].slice(0, FEED_SIZE));
    this.events.next(evt);
  }

  private async fetchCursor(familyId: string): Promise<number> {
    const res = await firstValueFrom(
      this.http.get<{ cursor: number }>(this.config.api(`/families/${familyId}/changes/cursor`)),
    );
    return res.cursor;
  }

  /**
   * Toma el cursor actual y, para el panel de actividad, los últimos cambios conocidos. El cursor es global,
   * así que se pide una ventana hacia atrás y se queda con lo más reciente de esta familia.
   */
  private async loadRecentActivity(familyId: string): Promise<void> {
    const cursor = await this.fetchCursor(familyId);
    this.cursor = cursor;
    this.baseline = cursor;
    this.seen.clear();
    try {
      const page = await firstValueFrom(
        this.http.get<ChangePage>(this.config.api(`/families/${familyId}/changes`), {
          params: { since: Math.max(0, cursor - 200), limit: 500 },
        }),
      );
      this.feed.set(
        page.changes
          .filter((e) => e.id <= cursor)
          .reverse()
          .slice(0, FEED_SIZE),
      );
    } catch {
      this.feed.set([]);
    }
  }
}

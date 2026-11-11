import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from './config';
import {
  AppNotification,
  LiveNotification,
  NotificationPage,
  NotificationPreferences,
  PushConfig,
} from './models';
import { RealtimeService } from './realtime.service';

/** Clave VAPID base64url → bytes para `pushManager.subscribe`. */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const pad = '='.repeat((4 - (value.length % 4)) % 4);
  const raw = atob((value + pad).replace(/-/g, '+').replace(/_/g, '/'));
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

export type PushState = 'unsupported' | 'denied' | 'off' | 'on';

/**
 * Centro de notificaciones (campana) y push web. Las nuevas llegan en vivo por la cola personal del WebSocket; con
 * la app cerrada llegan por push al service worker (`/sw.js`).
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly realtime = inject(RealtimeService);

  readonly items = signal<AppNotification[]>([]);
  readonly unread = signal(0);
  readonly loaded = signal(false);
  /** Última que llegó en vivo (para el aviso flotante). */
  readonly latest = signal<AppNotification | null>(null);
  readonly pushState = signal<PushState>('off');
  readonly hasUnread = computed(() => this.unread() > 0);

  private registration: Promise<ServiceWorkerRegistration | null> | null = null;

  constructor() {
    this.realtime.notifications.subscribe((n) => this.onLive(n));
  }

  get pushSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      'Notification' in window &&
      window.isSecureContext
    );
  }

  /** Registra el service worker y revisa si este navegador ya está suscrito. */
  start(): void {
    this.load().catch(() => undefined);
    if (!this.pushSupported) {
      this.pushState.set('unsupported');
      return;
    }
    this.registration ??= navigator.serviceWorker
      .register('/sw.js', { scope: '/' })
      .catch(() => null);
    this.refreshPushState().catch(() => undefined);
  }

  async load(): Promise<void> {
    const page = await firstValueFrom(
      this.http.get<NotificationPage>(this.config.api('/notifications'), {
        params: { limit: 30 },
      }),
    );
    this.items.set(page.items);
    this.unread.set(page.unread);
    this.loaded.set(true);
  }

  async markRead(n: AppNotification): Promise<void> {
    if (n.read) return;
    this.items.update((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    this.unread.update((u) => Math.max(0, u - 1));
    await firstValueFrom(this.http.post(this.config.api(`/notifications/${n.id}/read`), null));
  }

  async markAllRead(): Promise<void> {
    this.items.update((list) => list.map((x) => ({ ...x, read: true })));
    this.unread.set(0);
    await firstValueFrom(this.http.post(this.config.api('/notifications/read-all'), null));
  }

  clear(): void {
    this.items.set([]);
    this.unread.set(0);
    this.loaded.set(false);
    this.latest.set(null);
  }

  private onLive(live: LiveNotification): void {
    const n = live.notification;
    this.items.update((list) =>
      list.some((x) => x.id === n.id) ? list : [n, ...list].slice(0, 50),
    );
    this.unread.set(live.unread);
    this.latest.set(n);
  }

  // ------------------------------------------------------------ push web

  async refreshPushState(): Promise<void> {
    if (!this.pushSupported) {
      this.pushState.set('unsupported');
      return;
    }
    if (Notification.permission === 'denied') {
      this.pushState.set('denied');
      return;
    }
    const reg = await this.registration;
    const sub = await reg?.pushManager.getSubscription();
    this.pushState.set(sub && Notification.permission === 'granted' ? 'on' : 'off');
  }

  /** Pide permiso, suscribe este navegador con la clave VAPID del servidor y lo registra. */
  async enablePush(): Promise<void> {
    if (!this.pushSupported) throw new Error('Este navegador no admite notificaciones.');
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      this.pushState.set(permission === 'denied' ? 'denied' : 'off');
      throw new Error('No diste permiso para mostrar notificaciones.');
    }
    this.registration ??= navigator.serviceWorker.register('/sw.js', { scope: '/' });
    await this.registration;
    const reg = await navigator.serviceWorker.ready;
    const cfg = await firstValueFrom(
      this.http.get<PushConfig>(this.config.api('/notifications/push')),
    );
    if (!cfg.enabled) throw new Error('Las notificaciones push están desactivadas en el servidor.');
    const key = base64UrlToBytes(cfg.publicKey);
    let sub = await reg.pushManager.getSubscription();
    if (sub && !sameKey(sub.options.applicationServerKey, key)) {
      // El servidor cambió de clave VAPID: la suscripción vieja ya no sirve.
      await sub.unsubscribe();
      sub = null;
    }
    sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
    await firstValueFrom(
      this.http.post(this.config.api('/notifications/push/subscriptions'), sub.toJSON()),
    );
    this.pushState.set('on');
  }

  async disablePush(): Promise<void> {
    const reg = await this.registration;
    const sub = await reg?.pushManager.getSubscription();
    if (sub) {
      await firstValueFrom(
        this.http.delete(this.config.api('/notifications/push/subscriptions'), {
          body: { endpoint: sub.endpoint },
        }),
      ).catch(() => undefined);
      await sub.unsubscribe();
    }
    this.pushState.set('off');
  }

  /** Al cerrar sesión: este navegador deja de recibir avisos de esa cuenta. */
  async forgetBrowser(): Promise<void> {
    if (!this.pushSupported || !this.registration) return;
    const reg = await this.registration;
    const sub = await reg?.pushManager.getSubscription();
    if (sub) {
      await firstValueFrom(
        this.http.delete(this.config.api('/notifications/push/subscriptions'), {
          body: { endpoint: sub.endpoint },
        }),
      ).catch(() => undefined);
      await sub.unsubscribe().catch(() => undefined);
    }
    this.pushState.set('off');
  }

  async sendTest(): Promise<void> {
    await firstValueFrom(this.http.post(this.config.api('/notifications/push/test'), null));
  }

  preferences(): Promise<NotificationPreferences> {
    return firstValueFrom(
      this.http.get<NotificationPreferences>(this.config.api('/notifications/preferences')),
    );
  }

  savePreferences(p: NotificationPreferences): Promise<NotificationPreferences> {
    return firstValueFrom(
      this.http.put<NotificationPreferences>(this.config.api('/notifications/preferences'), p),
    );
  }
}

function sameKey(current: ArrayBuffer | null, key: Uint8Array): boolean {
  if (!current) return false;
  const a = new Uint8Array(current);
  return a.length === key.length && a.every((b, i) => b === key[i]);
}

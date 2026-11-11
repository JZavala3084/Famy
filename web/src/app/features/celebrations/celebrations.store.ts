import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from '../../core/config';
import { FamilyService } from '../../core/family.service';
import { Celebration, CelebrationInput } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';

/**
 * Cumpleaños y fechas especiales. Las ideas de regalo de un miembro no viajan en los eventos en vivo (para que la
 * persona festejada no las vea), así que ante cualquier cambio se vuelve a pedir la lista.
 */
@Injectable({ providedIn: 'root' })
export class CelebrationsStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);
  private familyId: string | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;

  readonly list = signal<Celebration[] | null>(null);

  constructor() {
    this.realtime.events.subscribe((e) => {
      if (e.familyId !== this.familyId) return;
      // También cambian con las altas y bajas de miembros (sus cumpleaños salen del perfil).
      if (e.entity === 'celebration' || e.entity === 'family.member') this.reloadSoon();
    });
    this.realtime.resync.subscribe(() => this.reloadSoon());
  }

  async load(force = false): Promise<void> {
    const fid = this.family.current()!.id;
    if (fid !== this.familyId) {
      this.familyId = fid;
      this.list.set(null);
    } else if (!force && this.list()) {
      return;
    }
    await this.realtime.ready;
    const list = await firstValueFrom(this.http.get<Celebration[]>(this.url('')));
    if (fid === this.familyId) this.list.set(list);
  }

  /** Guarda: crea el registro si es el cumpleaños de un miembro que aún no tenía (id nulo). */
  async save(c: Celebration | null, input: CelebrationInput): Promise<void> {
    if (c?.id) {
      await firstValueFrom(this.http.put(this.url(`/${c.id}`), { ...input, version: c.version }));
    } else {
      await firstValueFrom(this.http.post(this.url(''), input));
    }
    await this.load(true);
  }

  async remove(c: Celebration): Promise<void> {
    if (!c.id) return;
    await firstValueFrom(this.http.delete(this.url(`/${c.id}`)));
    await this.load(true);
  }

  private reloadSoon(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      if (this.familyId) this.load(true).catch(() => undefined);
    }, 300);
  }

  private url(path: string): string {
    return this.config.api(`/families/${this.familyId}/celebrations${path}`);
  }
}

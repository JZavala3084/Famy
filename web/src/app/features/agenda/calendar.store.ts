import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from '../../core/config';
import { addDays } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { CalendarEvent, EventInput, EventRange } from '../../core/models';
import { RangeCache } from '../../core/range-cache';
import { RealtimeService } from '../../core/realtime.service';
import { zonedInstant } from '../../core/dates';

/** Eventos del calendario por rango de fechas (zona de la familia), sincronizados con `calendar.event`. */
@Injectable({ providedIn: 'root' })
export class CalendarStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);
  private familyId: string | null = null;

  private readonly cache = new RangeCache<EventRange>((from, to) => {
    const tz = this.family.timezone();
    return firstValueFrom(
      this.http.get<EventRange>(this.url(''), {
        params: {
          from: zonedInstant(from, '00:00', tz),
          to: zonedInstant(addDays(to, 1), '00:00', tz),
        },
      }),
    );
  });

  constructor() {
    this.realtime.events.subscribe((e) => {
      if (e.entity === 'calendar.event' && e.familyId === this.familyId) this.cache.reloadSoon();
    });
    this.realtime.resync.subscribe(() => this.cache.reloadSoon(0));
  }

  /** Eventos que se cruzan con los días `[from, to]` (o `undefined` mientras carga). */
  range(from: string, to: string): EventRange | undefined {
    return this.cache.get(from, to);
  }

  async load(from: string, to: string): Promise<void> {
    this.ensureFamily();
    await this.realtime.ready;
    await this.cache.load(from, to);
  }

  async create(input: EventInput): Promise<CalendarEvent> {
    const e = await firstValueFrom(this.http.post<CalendarEvent>(this.url(''), input));
    this.cache.reloadSoon(0);
    return e;
  }

  async update(event: CalendarEvent, input: EventInput): Promise<CalendarEvent> {
    const e = await firstValueFrom(
      this.http.put<CalendarEvent>(this.url(`/${event.id}`), { ...input, version: event.version }),
    );
    this.cache.reloadSoon(0);
    return e;
  }

  async remove(event: CalendarEvent): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/${event.id}`)));
    this.cache.reloadSoon(0);
  }

  /** «Solo este día»: cancela una ocurrencia de un evento que se repite. */
  async cancelOccurrence(event: CalendarEvent, start: string): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/${event.id}/occurrences/${start}`)));
    this.cache.reloadSoon(0);
  }

  private ensureFamily(): void {
    const fid = this.family.current()!.id;
    if (fid !== this.familyId) {
      this.familyId = fid;
      this.cache.clear();
    }
  }

  private url(path: string): string {
    return this.config.api(`/families/${this.familyId}/events${path}`);
  }
}

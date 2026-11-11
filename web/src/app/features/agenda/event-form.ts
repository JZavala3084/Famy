import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { addDays, dayKey, timeKey, zonedInstant } from '../../core/dates';
import { FamilyService, MEMBER_COLORS } from '../../core/family.service';
import { CalendarEvent, EventInput } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { RepeatKind, WEEKDAYS, buildRrule, parseRepeat } from '../chores/rrule';
import { CalendarStore } from './calendar.store';

export const REMINDER_OPTIONS: [number, string][] = [
  [0, 'A la hora'],
  [10, '10 min antes'],
  [30, '30 min antes'],
  [60, '1 h antes'],
  [1440, '1 día antes'],
];

/** Diálogo para crear o editar un evento (toda la serie) o cancelar un solo día. */
@Component({
  selector: 'app-event-form',
  imports: [FormsModule, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="sheet" (close)="dialog.open || open.set(false)">
      @if (open()) {
        <form class="sheet-body" (ngSubmit)="save()">
          <h2>{{ event() ? 'Editar evento' : 'Nuevo evento' }}</h2>
          @if (event()?.rrule) {
            <p class="hint">Los cambios se aplican a todas las repeticiones.</p>
          }
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <div class="field">
            <label for="e-title">Evento</label>
            <input
              id="e-title"
              class="input"
              name="title"
              maxlength="120"
              placeholder="Fútbol de Diego"
              required
              [(ngModel)]="f.title"
            />
          </div>
          <label class="switch">
            <input type="checkbox" name="allDay" [(ngModel)]="f.allDay" />
            <span><b>Todo el día</b></span>
          </label>
          <div class="grid3">
            <div class="field">
              <label for="e-day">{{ f.allDay ? 'Desde' : 'Día' }}</label>
              <input id="e-day" class="input" name="day" type="date" required [(ngModel)]="f.day" />
            </div>
            @if (f.allDay) {
              <div class="field">
                <label for="e-end-day">Hasta</label>
                <input
                  id="e-end-day"
                  class="input"
                  name="endDay"
                  type="date"
                  [min]="f.day"
                  [(ngModel)]="f.endDay"
                />
              </div>
            } @else {
              <div class="field">
                <label for="e-start">Empieza</label>
                <input id="e-start" class="input" name="start" type="time" [(ngModel)]="f.start" />
              </div>
              <div class="field">
                <label for="e-end">Termina</label>
                <input id="e-end" class="input" name="end" type="time" [(ngModel)]="f.end" />
              </div>
            }
          </div>
          <div class="field">
            <label for="e-repeat">Se repite</label>
            <select id="e-repeat" class="input" name="repeat" [(ngModel)]="f.repeat">
              <option value="none">No se repite</option>
              <option value="daily">Todos los días</option>
              <option value="weekly">Días específicos de la semana</option>
              <option value="monthly">Cada mes</option>
              @if (f.repeat === 'custom') {
                <option value="custom">Personalizada ({{ f.raw }})</option>
              }
            </select>
          </div>
          @if (f.repeat === 'weekly') {
            <div class="chips-wrap" role="group" aria-label="Días">
              @for (d of weekdays; track d[0]) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="f.days.includes(d[0])"
                  [attr.aria-pressed]="f.days.includes(d[0])"
                  (click)="f.days = toggle(f.days, d[0])"
                >
                  {{ d[1] }}
                </button>
              }
            </div>
          }
          @if (family.isAdult()) {
            <div class="field">
              <span class="label">¿Quién va?</span>
              <div class="chips-wrap">
                @for (m of family.members(); track m.id) {
                  <button
                    type="button"
                    class="person"
                    [class.on]="f.people.includes(m.id)"
                    [attr.aria-pressed]="f.people.includes(m.id)"
                    (click)="f.people = toggle(f.people, m.id)"
                  >
                    <app-avatar [member]="m" [size]="26" /> {{ m.name }}
                  </button>
                }
              </div>
              <span class="hint">Sin nadie marcado, es para toda la familia.</span>
            </div>
          }
          <div class="field">
            <label for="e-where">Lugar (opcional)</label>
            <input
              id="e-where"
              class="input"
              name="location"
              maxlength="200"
              [(ngModel)]="f.location"
            />
          </div>
          <div class="field">
            <span class="label">Recordatorio</span>
            <div class="chips-wrap">
              @for (r of reminders; track r[0]) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="f.reminders.includes(r[0])"
                  [attr.aria-pressed]="f.reminders.includes(r[0])"
                  (click)="f.reminders = toggle(f.reminders, r[0])"
                >
                  {{ r[1] }}
                </button>
              }
            </div>
          </div>
          <div class="field">
            <span class="label">Color</span>
            <div class="chips-wrap" role="radiogroup" aria-label="Color">
              @for (c of colors; track c) {
                <button
                  type="button"
                  role="radio"
                  class="swatch"
                  [style.background]="c"
                  [attr.aria-checked]="f.color === c"
                  [attr.aria-label]="c"
                  (click)="f.color = f.color === c ? '' : c"
                >
                  @if (f.color === c) {
                    <app-icon name="check" [size]="16" [stroke]="3" />
                  }
                </button>
              }
            </div>
          </div>
          <div class="field">
            <label for="e-notes">Notas (opcional)</label>
            <textarea
              id="e-notes"
              class="input"
              name="notes"
              rows="2"
              maxlength="2000"
              [(ngModel)]="f.description"
            ></textarea>
          </div>

          <div class="sheet-actions">
            @if (event(); as e) {
              @if (e.rrule && occurrence()) {
                <button class="btn ghost" type="button" (click)="cancelDay(e)" [disabled]="busy()">
                  Quitar solo este día
                </button>
              }
              <button class="btn danger" type="button" (click)="remove(e)" [disabled]="busy()">
                <app-icon name="trash" [size]="18" /> {{ e.rrule ? 'Borrar serie' : 'Borrar' }}
              </button>
            }
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="close()">Cancelar</button>
            <button class="btn" type="submit" [disabled]="busy() || !f.title.trim() || !f.day">
              Guardar
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .grid3 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
      gap: 12px;
    }
    .chips-wrap {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .switch {
      display: flex;
      gap: 12px;
      align-items: center;
      cursor: pointer;
      font-size: 14px;
    }
    .switch input {
      width: 20px;
      height: 20px;
      margin: 0;
      accent-color: var(--primary);
    }
    .person {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: 40px;
      padding: 4px 12px 4px 6px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface);
      font-size: 14px;
      cursor: pointer;
    }
    .person.on {
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .swatch {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      border: 0;
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .grow {
      flex: 1;
    }
  `,
})
export class EventForm {
  protected readonly family = inject(FamilyService);
  private readonly auth = inject(AuthService);
  private readonly store = inject(CalendarStore);

  protected readonly weekdays = WEEKDAYS;
  protected readonly reminders = REMINDER_OPTIONS;
  protected readonly colors = MEMBER_COLORS;
  protected readonly open = signal(false);
  protected readonly event = signal<CalendarEvent | null>(null);
  protected readonly occurrence = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected f = this.blank('');
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  /** Nuevo evento el día `day`, o editar `event` (abierto desde la ocurrencia que empieza en `occurrence`). */
  show(event: CalendarEvent | null, day: string, occurrence: string | null = null) {
    const tz = this.family.timezone();
    this.event.set(event);
    this.occurrence.set(occurrence);
    this.error.set(null);
    if (event) {
      const r = parseRepeat(event.rrule);
      const lastDay = dayKey(new Date(new Date(event.endsAt).getTime() - 1), tz);
      this.f = {
        title: event.title,
        description: event.description ?? '',
        location: event.location ?? '',
        allDay: event.allDay,
        day: dayKey(event.startsAt, tz),
        endDay: event.allDay ? lastDay : '',
        start: timeKey(event.startsAt, tz),
        end: timeKey(event.endsAt, tz),
        repeat: r.kind,
        days: r.days,
        raw: r.raw,
        people: [...event.participantIds],
        reminders: [...event.reminderMinutes],
        color: event.color ?? '',
      };
    } else {
      this.f = this.blank(day);
    }
    this.open.set(true);
    this.dialog().nativeElement.showModal();
  }

  close() {
    this.dialog().nativeElement.close();
  }

  protected toggle<T>(list: T[], value: T): T[] {
    return list.includes(value) ? list.filter((x) => x !== value) : [...list, value];
  }

  async save() {
    const f = this.f;
    const tz = this.family.timezone();
    let startsAt: string;
    let endsAt: string;
    if (f.allDay) {
      startsAt = zonedInstant(f.day, '00:00', tz);
      const last = f.endDay && f.endDay >= f.day ? f.endDay : f.day;
      endsAt = zonedInstant(addDays(last, 1), '00:00', tz);
    } else {
      startsAt = zonedInstant(f.day, f.start || '09:00', tz);
      // Si termina «antes» de empezar, es que cruza la medianoche.
      const endDay = f.end && f.end < (f.start || '09:00') ? addDays(f.day, 1) : f.day;
      endsAt = f.end
        ? zonedInstant(endDay, f.end, tz)
        : new Date(new Date(startsAt).getTime() + 3_600_000).toISOString();
    }
    const me = this.auth.profile()?.id;
    const input: EventInput = {
      title: f.title.trim(),
      description: f.description.trim() || null,
      location: f.location.trim() || null,
      startsAt,
      endsAt,
      allDay: f.allDay,
      rrule: buildRrule({ kind: f.repeat, days: f.days, raw: f.raw }),
      color: f.color || null,
      participantIds: this.family.isAdult() ? f.people : me ? [me] : [],
      reminderMinutes: f.reminders,
    };
    const current = this.event();
    await this.run(async () => {
      if (current) await this.store.update(current, input);
      else await this.store.create(input);
    });
  }

  async remove(e: CalendarEvent) {
    const what = e.rrule ? `todas las repeticiones de «${e.title}»` : `«${e.title}»`;
    if (!confirm(`¿Borrar ${what}?`)) return;
    await this.run(() => this.store.remove(e));
  }

  async cancelDay(e: CalendarEvent) {
    const start = this.occurrence();
    if (!start) return;
    await this.run(() => this.store.cancelOccurrence(e, start));
  }

  private async run(action: () => Promise<unknown>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.close();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  private blank(day: string) {
    return {
      title: '',
      description: '',
      location: '',
      allDay: false,
      day,
      endDay: '',
      start: '18:00',
      end: '19:00',
      repeat: 'none' as RepeatKind,
      days: [] as string[],
      raw: null as string | null,
      people: [] as string[],
      reminders: [30] as number[],
      color: '',
    };
  }
}

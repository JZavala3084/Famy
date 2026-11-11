import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import {
  addDays,
  addMonths,
  dayKey,
  formatDate,
  longDate,
  monthStart,
  monthTitle,
  weekStart,
} from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { CelebrationsStore } from '../celebrations/celebrations.store';
import { ChoresStore } from '../chores/chores.store';
import { describeRepeat } from '../chores/rrule';
import { PaymentsStore } from '../payments/payments.store';
import { AgendaItem, buildAgenda, forMember } from './agenda-items';
import { CalendarStore } from './calendar.store';
import { EventForm } from './event-form';

type View = 'week' | 'month';
const WEEK_LETTERS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Agenda familiar (prototipo «Horarios»): eventos, pagos, cumpleaños y tareas en una sola vista. */
@Component({
  selector: 'app-agenda',
  imports: [Avatar, Icon, LiveBadge, EventForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div class="col">
          <p class="muted">{{ monthLabel() }}</p>
          <h1>Agenda</h1>
        </div>
        <span class="head-actions">
          <app-live-badge />
          <button class="btn" type="button" (click)="form().show(null, selected())">
            <app-icon name="plus" [size]="18" /> Evento
          </button>
        </span>
      </header>

      @if (error()) {
        <div class="alert error" role="alert">
          <span class="grow">{{ error() }}</span>
          <button class="icon-btn" type="button" aria-label="Cerrar" (click)="error.set(null)">
            <app-icon name="x" [size]="18" />
          </button>
        </div>
      }

      <div class="toolbar">
        <div class="segmented" role="tablist">
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="view() === 'week'"
            (click)="view.set('week')"
          >
            Semana
          </button>
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="view() === 'month'"
            (click)="view.set('month')"
          >
            Mes
          </button>
        </div>
        <div class="nav">
          <button class="icon-btn" type="button" aria-label="Anterior" (click)="move(-1)">
            <app-icon name="back" />
          </button>
          <button class="btn ghost small" type="button" (click)="select(today())">Hoy</button>
          <button class="icon-btn" type="button" aria-label="Siguiente" (click)="move(1)">
            <app-icon name="next" />
          </button>
        </div>
      </div>

      <div class="chips" role="group" aria-label="Filtrar por persona">
        <button type="button" class="chip" [class.on]="!who()" (click)="who.set(null)">
          Todos
        </button>
        @for (m of family.members(); track m.id) {
          <button type="button" class="chip" [class.on]="who() === m.id" (click)="who.set(m.id)">
            <app-avatar [member]="m" [size]="20" /> {{ m.name }}
          </button>
        }
      </div>

      @if (view() === 'week') {
        <div class="week" role="tablist" aria-label="Días de la semana">
          @for (d of week(); track d.key) {
            <button
              type="button"
              role="tab"
              class="wday"
              [class.on]="d.key === selected()"
              [class.today]="d.key === today()"
              [attr.aria-selected]="d.key === selected()"
              [attr.aria-label]="d.label"
              (click)="select(d.key)"
            >
              <span class="w">{{ d.letter }}</span>
              <span class="n">{{ d.n }}</span>
              <span class="dots">
                @for (c of d.colors; track $index) {
                  <i [style.background]="c"></i>
                }
              </span>
            </button>
          }
        </div>

        <section class="day">
          <h2>{{ dayTitle() }}</h2>
          @if (!loaded()) {
            <div class="card empty">Cargando…</div>
          } @else {
            @for (it of dayItems(); track it.key) {
              <button type="button" class="item" (click)="open(it)" [style.--c]="it.color">
                <span class="time">
                  @if (it.time) {
                    <b>{{ it.time }}</b>
                    @if (it.endTime) {
                      <span>{{ it.endTime }}</span>
                    }
                  } @else {
                    <b class="allday">Todo el día</b>
                  }
                </span>
                <span class="body">
                  <span class="title">
                    @switch (it.kind) {
                      @case ('payment') {
                        <app-icon name="receipt" [size]="16" />
                      }
                      @case ('celebration') {
                        <app-icon name="gift" [size]="16" />
                      }
                      @case ('chore') {
                        <app-icon name="tasks" [size]="16" />
                      }
                    }
                    {{ it.title }}
                  </span>
                  <span class="meta">{{ meta(it) }}</span>
                </span>
                <span class="who">
                  @for (id of it.memberIds.slice(0, 3); track id) {
                    <app-avatar [member]="family.member(id)" [size]="24" />
                  }
                </span>
              </button>
            } @empty {
              <div class="card empty">
                Nada para este día.
                <button class="link-btn" type="button" (click)="form().show(null, selected())">
                  Agregar un evento
                </button>
              </div>
            }
          }
        </section>
      } @else {
        <section class="card month" aria-label="Mes">
          <div class="mhead">
            @for (l of letters; track $index) {
              <span>{{ l }}</span>
            }
          </div>
          <div class="mgrid">
            @for (c of monthCells(); track c.key) {
              <button
                type="button"
                class="cell"
                [class.out]="!c.inMonth"
                [class.today]="c.key === today()"
                [class.on]="c.key === selected()"
                (click)="select(c.key); view.set('week')"
              >
                <span class="n">{{ c.n }}</span>
                @for (it of c.items.slice(0, 3); track it.key) {
                  <span class="bar" [style.--c]="it.color">{{ it.title }}</span>
                }
                @if (c.items.length > 3) {
                  <span class="more">+{{ c.items.length - 3 }}</span>
                }
              </button>
            }
          </div>
        </section>
      }
    </div>
    <app-event-form />
  `,
  styleUrl: './agenda.css',
})
export class Agenda {
  protected readonly family = inject(FamilyService);
  private readonly calendar = inject(CalendarStore);
  private readonly payments = inject(PaymentsStore);
  private readonly celebrations = inject(CelebrationsStore);
  private readonly chores = inject(ChoresStore);
  private readonly router = inject(Router);
  protected readonly form = viewChild.required(EventForm);

  /** `?dia=YYYY-MM-DD` (los recordatorios enlazan al día del evento). */
  readonly dia = input<string | undefined>();

  protected readonly letters = WEEK_LETTERS;
  protected readonly view = signal<View>('week');
  protected readonly who = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly clock = signal(Date.now());
  protected readonly today = computed(() => {
    this.clock();
    return dayKey(new Date(), this.family.timezone());
  });
  protected readonly selected = signal(dayKey(new Date(), this.family.timezone()));

  /** Rango cargado: la cuadrícula del mes del día elegido (semanas completas). */
  private readonly range = computed(() => {
    const first = monthStart(this.selected());
    const from = weekStart(first);
    const last = addDays(addMonths(first, 1), -1);
    const to = addDays(weekStart(last), 6);
    return { from, to };
  });

  private readonly items = computed<AgendaItem[]>(() => {
    const { from, to } = this.range();
    return buildAgenda({
      from,
      to,
      timeZone: this.family.timezone(),
      events: this.calendar.range(from, to),
      payments: this.family.isAdult() ? this.payments.occurrences(from, to)?.occurrences : [],
      celebrations: this.celebrations.list(),
      chores: this.chores.all(),
      memberName: (id) => this.family.member(id).name,
      memberColor: (id) => this.family.member(id).color,
    }).filter((it) => forMember(it, this.who()));
  });

  protected readonly loaded = computed(() => {
    const { from, to } = this.range();
    return !!this.calendar.range(from, to);
  });

  private readonly byDay = computed(() => {
    const map = new Map<string, AgendaItem[]>();
    for (const it of this.items()) {
      if (!map.has(it.day)) map.set(it.day, []);
      map.get(it.day)!.push(it);
    }
    return map;
  });

  protected readonly week = computed(() => {
    const start = weekStart(this.selected());
    return WEEK_LETTERS.map((letter, i) => {
      const key = addDays(start, i);
      const colors = [...new Set((this.byDay().get(key) ?? []).map((x) => x.color))].slice(0, 3);
      return { key, letter, n: Number(key.slice(8)), label: longDate(key), colors };
    });
  });

  protected readonly dayItems = computed(() => this.byDay().get(this.selected()) ?? []);
  protected readonly dayTitle = computed(() => {
    const d = this.selected();
    if (d === this.today()) return `Hoy · ${longDate(d)}`;
    if (d === addDays(this.today(), 1)) return `Mañana · ${longDate(d)}`;
    return longDate(d);
  });
  protected readonly monthLabel = computed(() => monthTitle(this.selected()));

  protected readonly monthCells = computed(() => {
    const { from, to } = this.range();
    const month = this.selected().slice(0, 7);
    const cells = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      cells.push({
        key: d,
        n: Number(d.slice(8)),
        inMonth: d.startsWith(month),
        items: this.byDay().get(d) ?? [],
      });
    }
    return cells;
  });

  constructor() {
    effect(() => {
      const d = this.dia();
      if (d && /^\d{4}-\d{2}-\d{2}$/.test(d)) untracked(() => this.selected.set(d));
    });
    effect(() => {
      const { from, to } = this.range();
      untracked(() => {
        const fail = (e: unknown) => this.error.set(errorMessage(e));
        this.calendar.load(from, to).catch(fail);
        this.payments.load(from, to).catch(fail);
      });
    });
    this.celebrations.load().catch((e) => this.error.set(errorMessage(e)));
    this.chores.load().catch(() => undefined);
    const timer = setInterval(() => this.clock.set(Date.now()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected select(day: string) {
    this.selected.set(day);
  }

  protected move(step: number) {
    const d = this.selected();
    if (this.view() === 'week') this.selected.set(addDays(d, step * 7));
    else {
      const target = addMonths(d, step);
      const day = Math.min(Number(d.slice(8)), 28);
      this.selected.set(`${target.slice(0, 8)}${String(day).padStart(2, '0')}`);
    }
  }

  protected meta(it: AgendaItem): string {
    if (it.kind !== 'event' || !it.event?.rrule) return it.meta;
    return `${it.meta} · ${describeRepeat(it.event.rrule).toLowerCase()}`;
  }

  protected open(it: AgendaItem) {
    switch (it.kind) {
      case 'event':
        this.form().show(it.event!, it.day, it.occurrenceStart ?? null);
        break;
      case 'payment':
        this.router.navigate(['/pagos'], { queryParams: { mes: it.day.slice(0, 7) } });
        break;
      case 'celebration':
        this.router.navigate(['/cumpleanos']);
        break;
      case 'chore':
        this.router.navigate(['/tareas']);
        break;
    }
  }

  protected shortDay(key: string) {
    return formatDate(key, { day: 'numeric', month: 'short' });
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  addDays,
  addMonths,
  dayKey,
  formatDate,
  longToday,
  monthStart,
  relativeTime,
} from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { Chore, ShoppingItem } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { RealtimeService } from '../../core/realtime.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { ChoresStore } from '../chores/chores.store';
import { ShoppingStore } from '../shopping/shopping.store';
import { AgendaItem, buildAgenda } from '../agenda/agenda-items';
import { CalendarStore } from '../agenda/calendar.store';
import { untilLabel } from '../celebrations/celebrations';
import { CelebrationsStore } from '../celebrations/celebrations.store';
import { totals } from '../payments/payments';
import { PaymentsStore } from '../payments/payments.store';
import { actorOf, describeChange } from './activity';

/** Panel familiar (prototipo «WebInicio»): resumen del día, agenda de la semana y actividad en vivo. */
@Component({
  selector: 'app-home',
  imports: [RouterLink, Avatar, Icon, LiveBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div class="col">
          <p class="muted">{{ todayText() }}</p>
          <h1>Hola, {{ firstName() }}</h1>
        </div>
        <div class="head-actions">
          <app-live-badge />
          @if (family.canInvite()) {
            <a class="btn" routerLink="/familia" [queryParams]="{ invitar: 1 }">
              <app-icon name="userPlus" [size]="18" /> Invitar a la familia
            </a>
          }
        </div>
      </header>

      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }

      <div class="members" aria-label="Miembros">
        @for (m of family.members(); track m.id) {
          <span class="member"
            ><app-avatar [member]="m" [size]="36" /><span>{{ m.name }}</span></span
          >
        }
      </div>

      <div class="stats">
        <a class="stat primary" routerLink="/compras">
          <span class="label">Compras pendientes</span>
          <span class="value">{{ shopping.totalPending() }}</span>
          <span class="sub">{{ cartText() }}</span>
        </a>
        <a class="stat" routerLink="/tareas">
          <span class="label">Tareas de hoy</span>
          <span class="value">{{ chores.todayDone() }} de {{ chores.todayList().length }}</span>
          <span class="sub">{{ pendingChoresText() }}</span>
        </a>
        @if (family.isAdult()) {
          <a class="stat" routerLink="/pagos">
            <span class="label">Por pagar este mes</span>
            <span class="value">{{ toPay() }}</span>
            <span class="sub">{{ payText() }}</span>
          </a>
        } @else {
          <a class="stat" routerLink="/tareas">
            <span class="label">Mis puntos esta semana</span>
            <span class="value">{{ myPoints() }}</span>
            <span class="sub">{{ leaderText() }}</span>
          </a>
        }
        <a class="stat accent" routerLink="/cumpleanos">
          <span class="label">Próximo cumpleaños</span>
          @if (nextCelebration(); as c) {
            <span class="value">{{ untilLabel(c.daysUntil) }}</span>
            <span class="sub">{{ c.personName }}</span>
          } @else {
            <span class="value">—</span>
            <span class="sub">Agrega fechas especiales</span>
          }
        </a>
      </div>

      <div class="panels">
        <section class="card panel">
          <div class="card-head">
            <h2>Compras · {{ firstList()?.name ?? 'sin listas' }}</h2>
            <a class="small" routerLink="/compras">Ver todo</a>
          </div>
          @if (firstList(); as l) {
            @for (it of shopItems(); track it.id) {
              <label class="check-row">
                <input type="checkbox" [checked]="it.checked" (change)="toggleItem(it)" />
                <span class="grow" [class.struck]="it.checked">{{ it.name }}</span>
                <span class="muted small">{{ family.member(it.createdBy).name }}</span>
              </label>
            } @empty {
              <p class="muted small">La lista está vacía.</p>
            }
          } @else {
            <p class="muted small">
              Crea tu primera lista en <a routerLink="/compras">Compras</a>.
            </p>
          }
        </section>

        <section class="card panel">
          <div class="card-head">
            <h2>Tareas de hoy</h2>
            <a class="small" routerLink="/tareas">Ver todo</a>
          </div>
          @for (c of todayChores(); track c.id) {
            @let done = chores.isDoneToday(c);
            <label class="check-row">
              <input
                type="checkbox"
                [checked]="done"
                [disabled]="!canMark(c)"
                (change)="toggleChore(c, done)"
              />
              <span class="grow" [class.struck]="done">{{ c.title }}</span>
              <span class="muted small">{{ whoFor(c) }}</span>
            </label>
          } @empty {
            <p class="muted small">No hay tareas para hoy.</p>
          }
        </section>

        <section class="card panel">
          <div class="card-head"><h2>Actividad reciente</h2></div>
          @for (f of feed(); track f.id) {
            <div class="feed">
              <app-avatar [member]="family.member(f.actor)" [size]="28" />
              <span class="grow">
                <b>{{ family.member(f.actor).name }}</b> {{ f.text }}<br />
                <span class="muted small">{{ f.when }}</span>
              </span>
            </div>
          } @empty {
            <p class="muted small">Aquí verás lo que hace tu familia, al instante.</p>
          }
        </section>

        <section class="card panel">
          <div class="card-head">
            <h2>Próximos 7 días</h2>
            <a class="small" routerLink="/agenda">Ver agenda</a>
          </div>
          @for (it of upcoming(); track it.key) {
            <a class="up" [routerLink]="linkFor(it)" [queryParams]="paramsFor(it)">
              <span class="when" [style.color]="it.color">
                <b>{{ shortDay(it.day) }}</b>
                <span>{{ it.time ?? 'Todo el día' }}</span>
              </span>
              <span class="grow ellipsis">{{ it.title }}</span>
            </a>
          } @empty {
            <p class="muted small">Nada en la agenda esta semana.</p>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .col {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .head-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .members {
      display: flex;
      gap: 14px;
      overflow-x: auto;
      scrollbar-width: none;
    }
    .member {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 600;
      white-space: nowrap;
    }
    .stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 16px;
    }
    .stat {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--r-card);
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: inherit;
      text-decoration: none;
    }
    .stat:hover {
      border-color: var(--border-strong);
      color: inherit;
    }
    .stat .label {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-2);
    }
    .stat .value {
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.02em;
    }
    .stat .sub {
      font-size: 13px;
      color: var(--text-2);
    }
    .stat.primary {
      background: var(--primary-soft);
      border-color: var(--primary-soft);
    }
    .stat.primary .label {
      color: var(--primary);
    }
    .stat.accent {
      background: var(--accent-soft);
      border-color: var(--accent-soft);
    }
    .stat.accent .label {
      color: var(--accent-strong);
    }
    .panels {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 16px;
      align-items: start;
    }
    .panel {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .card-head a {
      font-weight: 600;
    }
    .check-row {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 14px;
      min-height: 32px;
      cursor: pointer;
    }
    .check-row input {
      width: 18px;
      height: 18px;
      margin: 0;
      accent-color: var(--primary);
    }
    .struck {
      color: var(--text-2);
      text-decoration: line-through;
    }
    .feed {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      font-size: 13px;
      line-height: 1.45;
    }
    .up {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 0;
      color: inherit;
      text-decoration: none;
      font-size: 14px;
    }
    .up + .up {
      border-top: 1px solid var(--divider);
    }
    .when {
      display: flex;
      flex-direction: column;
      width: 86px;
      flex: none;
      font-size: 12px;
    }
    .when span {
      color: var(--text-2);
    }
    .ellipsis {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
})
export class Home {
  protected readonly family = inject(FamilyService);
  protected readonly shopping = inject(ShoppingStore);
  protected readonly chores = inject(ChoresStore);
  private readonly auth = inject(AuthService);
  private readonly realtime = inject(RealtimeService);

  private readonly calendar = inject(CalendarStore);
  private readonly payments = inject(PaymentsStore);
  private readonly celebrations = inject(CelebrationsStore);
  protected readonly untilLabel = untilLabel;
  protected readonly error = signal<string | null>(null);
  /** Se refresca cada minuto para «hace N min». */
  private readonly tick = signal(Date.now());

  protected readonly todayText = computed(() => {
    this.tick();
    return longToday(this.family.timezone());
  });
  protected readonly firstName = computed(
    () => (this.auth.profile()?.displayName ?? '').split(' ')[0] || 'familia',
  );
  protected readonly firstList = computed(() => this.shopping.lists()[0] ?? null);
  protected readonly shopItems = computed(() => {
    const l = this.firstList();
    return l ? this.shopping.items(l.id).slice(0, 6) : [];
  });
  protected readonly todayChores = computed(() => this.chores.todayList().slice(0, 6));

  protected readonly cartText = computed(() => {
    const l = this.firstList();
    const n = l ? this.shopping.checkedCount(l.id) : 0;
    return l ? `${n} ya en el carrito` : 'Sin listas todavía';
  });
  protected readonly pendingChoresText = computed(() => {
    const pending = this.chores.todayList().length - this.chores.todayDone();
    return pending === 0 ? '¡Todo listo por hoy!' : `${pending} por hacer`;
  });
  protected readonly myPoints = computed(() => {
    const me = this.auth.profile()?.id;
    return this.chores.scoreboard()?.members.find((m) => m.userId === me)?.points ?? 0;
  });
  protected readonly leaderText = computed(() => {
    const top = [...(this.chores.scoreboard()?.members ?? [])].sort(
      (a, b) => b.points - a.points,
    )[0];
    return top && top.points > 0
      ? `Va a la cabeza: ${this.family.member(top.userId).name}`
      : 'Lunes a domingo';
  });

  private readonly today = computed(() => {
    this.tick();
    return dayKey(new Date(), this.family.timezone());
  });
  private readonly week = computed(() => ({ from: this.today(), to: addDays(this.today(), 6) }));
  private readonly month = computed(() => {
    const from = monthStart(this.today());
    return { from, to: addDays(addMonths(from, 1), -1) };
  });
  protected readonly upcoming = computed(() => {
    const { from, to } = this.week();
    return buildAgenda({
      from,
      to,
      timeZone: this.family.timezone(),
      events: this.calendar.range(from, to),
      payments: this.family.isAdult() ? this.payments.occurrences(from, to)?.occurrences : [],
      celebrations: this.celebrations.list(),
      chores: [],
      memberName: (id) => this.family.member(id).name,
      memberColor: (id) => this.family.member(id).color,
    }).slice(0, 7);
  });
  private readonly monthPayments = computed(() => {
    const { from, to } = this.month();
    return this.payments.occurrences(from, to)?.occurrences ?? [];
  });
  protected readonly toPay = computed(() =>
    totals(
      this.monthPayments().filter((o) => o.status !== 'PAID'),
      this.family.current()?.currency ?? 'USD',
    ),
  );
  protected readonly payText = computed(() => {
    const list = this.monthPayments();
    const late = list.filter((o) => o.status === 'OVERDUE').length;
    const left = list.filter((o) => o.status !== 'PAID').length;
    if (late) return `${late} vencido(s)`;
    return left ? `${left} pendiente(s)` : 'Todo pagado';
  });
  protected readonly nextCelebration = computed(
    () =>
      [...(this.celebrations.list() ?? [])]
        .filter((c) => c.daysUntil !== null)
        .sort((a, b) => a.daysUntil! - b.daysUntil!)[0] ?? null,
  );

  protected readonly feed = computed(() => {
    this.tick();
    const title = (id: string) => this.chores.all().find((c) => c.id === id)?.title ?? null;
    return this.realtime
      .feed()
      .map((e) => ({
        id: e.id,
        actor: actorOf(e),
        text: describeChange(e, title),
        when: relativeTime(e.occurredAt),
      }))
      .filter((f) => f.text)
      .slice(0, 8);
  });

  constructor() {
    const fail = (e: unknown) => this.error.set(errorMessage(e));
    this.shopping.loadLists().catch(fail);
    this.chores.load().catch(fail);
    effect(() => {
      const l = this.firstList();
      if (l) untracked(() => this.shopping.loadItems(l.id).catch(fail));
    });
    this.celebrations.load().catch(fail);
    effect(() => {
      const w = this.week();
      const m = this.month();
      untracked(() => {
        this.calendar.load(w.from, w.to).catch(fail);
        this.payments.load(w.from, w.to).catch(fail);
        this.payments.load(m.from, m.to).catch(fail);
      });
    });
    const timer = setInterval(() => this.tick.set(Date.now()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected shortDay(day: string): string {
    if (day === this.today()) return 'Hoy';
    if (day === addDays(this.today(), 1)) return 'Mañana';
    return formatDate(day, { weekday: 'short', day: 'numeric' });
  }

  protected linkFor(it: AgendaItem): string {
    return it.kind === 'payment' ? '/pagos' : it.kind === 'celebration' ? '/cumpleanos' : '/agenda';
  }

  protected paramsFor(it: AgendaItem): Record<string, string> {
    if (it.kind === 'payment') return { mes: it.day.slice(0, 7) };
    return it.kind === 'event' ? { dia: it.day } : {};
  }

  /** Quién la hizo hoy o, si falta, a quién le toca. */
  protected whoFor(c: Chore): string {
    const id = this.chores.completionToday(c)?.completedBy ?? c.assigneeId;
    return id ? this.family.member(id).name : '';
  }

  protected canMark(c: Chore) {
    return this.family.isAdult() || c.assigneeId === this.auth.profile()?.id;
  }

  async toggleItem(it: ShoppingItem) {
    try {
      await this.shopping.toggle(it, this.auth.profile()?.id ?? '');
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  async toggleChore(c: Chore, done: boolean) {
    try {
      if (done) await this.chores.undo(c);
      else await this.chores.complete(c);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}

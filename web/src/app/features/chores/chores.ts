import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { addDays, dayKey, formatDay, formatTime } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { Chore } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { ChoreForm } from './chore-form';
import { ChoresStore } from './chores.store';
import { describeRepeat } from './rrule';

type Tab = 'today' | 'week' | 'rotation';

interface DayGroup {
  key: string;
  label: string;
  chores: Chore[];
}

@Component({
  selector: 'app-chores',
  imports: [Avatar, Icon, LiveBadge, ChoreForm, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <h1>Tareas del hogar</h1>
        <span class="head-actions">
          <app-live-badge />
          @if (family.isAdult()) {
            <button
              class="icon-btn primary"
              type="button"
              aria-label="Nueva tarea"
              (click)="form().show(null)"
            >
              <app-icon name="plus" [size]="22" />
            </button>
          }
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

      <div class="segmented" role="tablist">
        <button
          type="button"
          role="tab"
          [attr.aria-selected]="tab() === 'today'"
          (click)="tab.set('today')"
        >
          Hoy
        </button>
        <button
          type="button"
          role="tab"
          [attr.aria-selected]="tab() === 'week'"
          (click)="tab.set('week')"
        >
          Semana
        </button>
        <button
          type="button"
          role="tab"
          [attr.aria-selected]="tab() === 'rotation'"
          (click)="tab.set('rotation')"
        >
          Rotación
        </button>
      </div>

      @if (!store.loaded()) {
        <div class="card empty">Cargando tareas…</div>
      } @else {
        @switch (tab()) {
          @case ('today') {
            <section class="card progress">
              <div class="ring" [style.background]="ring()">
                <span>{{ store.todayDone() }}/{{ store.todayList().length }}</span>
              </div>
              <div class="col">
                <b>Progreso de hoy</b>
                <span class="muted small">{{ leaderText() }}</span>
              </div>
            </section>
            @if (store.todayList().length === 0) {
              <div class="card empty">No hay tareas para hoy. 🎉</div>
            } @else {
              <ul class="tasks">
                @for (c of store.todayList(); track c.id) {
                  <ng-container *ngTemplateOutlet="row; context: { $implicit: c }" />
                }
              </ul>
            }
          }
          @case ('week') {
            @for (g of week(); track g.key) {
              <section class="group">
                <h2 class="day">{{ g.label }}</h2>
                <ul class="tasks">
                  @for (c of g.chores; track c.id) {
                    <ng-container *ngTemplateOutlet="row; context: { $implicit: c }" />
                  }
                </ul>
              </section>
            } @empty {
              <div class="card empty">Nada pendiente esta semana.</div>
            }
          }
          @case ('rotation') {
            @for (c of rotations(); track c.id) {
              <section class="card rot">
                <div class="card-head">
                  <span class="col">
                    <b>{{ c.title }}</b>
                    <span class="muted small"
                      >{{ repeat(c.rrule)
                      }}{{ c.dueAt ? ' · siguiente ' + day(c.dueAt) : '' }}</span
                    >
                  </span>
                  @if (family.isAdult()) {
                    <button
                      class="icon-btn"
                      type="button"
                      [attr.aria-label]="'Editar ' + c.title"
                      (click)="form().show(c)"
                    >
                      <app-icon name="edit" [size]="18" />
                    </button>
                  }
                </div>
                <ol class="order">
                  @for (id of c.rotationOrder ?? []; track id) {
                    <li [class.turn]="id === c.assigneeId">
                      <app-avatar [member]="family.member(id)" [size]="28" />
                      <span>{{ family.member(id).name }}</span>
                      @if (id === c.assigneeId) {
                        <span class="pill primary">Le toca</span>
                      }
                    </li>
                  }
                </ol>
              </section>
            } @empty {
              <div class="card empty">
                No hay tareas en rotación. Crea una tarea que se repita y activa «Rotar entre
                miembros».
              </div>
            }
          }
        }

        @if (store.scoreboard(); as board) {
          @if (board.members.length) {
            <section class="card">
              <div class="card-head"><h2>Puntos de la semana</h2></div>
              <ul class="board">
                @for (m of board.members; track m.userId) {
                  <li>
                    <app-avatar [member]="family.member(m.userId)" [size]="28" />
                    <span class="grow">{{ family.member(m.userId).name }}</span>
                    <span class="muted small"
                      >{{ m.completions }} {{ m.completions === 1 ? 'tarea' : 'tareas' }}</span
                    >
                    <b>{{ m.points }} {{ m.points === 1 ? 'pt' : 'pts' }}</b>
                  </li>
                }
              </ul>
            </section>
          }
        }
      }
    </div>

    <ng-template #row let-c>
      @let done = store.isDoneToday(c);
      <li class="task" [class.done]="done" [class.late]="!done && isLate(c)">
        <button
          type="button"
          class="toggle"
          role="checkbox"
          [attr.aria-checked]="done"
          [disabled]="!canMark(c) || busyId() === c.id"
          (click)="toggle(c, done)"
        >
          <span class="ring-check"><app-icon name="check" [size]="15" [stroke]="3" /></span>
          <span class="col grow">
            <span class="title">{{ c.title }}</span>
            <span class="meta">{{ meta(c) }}</span>
          </span>
        </button>
        @if (c.points) {
          <span class="pts" title="Puntos">{{ c.points }} {{ c.points === 1 ? 'pt' : 'pts' }}</span>
        }
        @if (c.assigneeId) {
          <app-avatar [member]="family.member(c.assigneeId)" [size]="28" />
        }
        @if (family.isAdult()) {
          <button
            class="icon-btn"
            type="button"
            [attr.aria-label]="'Editar ' + c.title"
            (click)="form().show(c)"
          >
            <app-icon name="edit" [size]="18" />
          </button>
        }
      </li>
    </ng-template>

    <app-chore-form />
  `,
  styles: `
    .head-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .col {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .alert {
      align-items: center;
    }
    .progress {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 16px;
    }
    .ring {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex: none;
    }
    .ring span {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: var(--surface);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 800;
    }
    .tasks {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .task {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 64px;
      border: 1px solid var(--border);
      border-radius: 16px;
      background: var(--surface);
      padding: 0 8px 0 0;
    }
    .toggle {
      flex: 1;
      min-width: 0;
      min-height: 62px;
      border: 0;
      background: transparent;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 6px 10px 14px;
      text-align: left;
      cursor: pointer;
      border-radius: 16px;
    }
    .toggle:disabled {
      cursor: default;
    }
    .ring-check {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      border: 2px solid var(--check-off);
      background: var(--surface);
      color: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      flex: none;
    }
    .task.done .ring-check {
      border-color: var(--primary);
      background: var(--primary);
      color: #fff;
    }
    .title {
      font-size: 15px;
      font-weight: 600;
    }
    .task.done .title {
      color: var(--text-2);
      text-decoration: line-through;
    }
    .meta {
      font-size: 12px;
      color: var(--text-2);
    }
    .task.late .meta {
      color: var(--accent-strong);
      font-weight: 600;
    }
    .pts {
      font-size: 12px;
      font-weight: 700;
      color: var(--text-2);
      white-space: nowrap;
    }
    .group {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .day {
      font-size: 14px;
      color: var(--text-2);
      text-transform: capitalize;
    }
    .rot {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .order,
    .board {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .order li,
    .board li {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 14px;
    }
    .order li:not(.turn) {
      color: var(--text-2);
    }
    .board {
      margin-top: 12px;
    }
  `,
})
export class Chores {
  protected readonly store = inject(ChoresStore);
  protected readonly family = inject(FamilyService);
  private readonly auth = inject(AuthService);
  protected readonly form = viewChild.required(ChoreForm);

  protected readonly tab = signal<Tab>('today');
  protected readonly error = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);

  protected readonly ring = computed(() => {
    const total = this.store.todayList().length;
    const deg = total ? Math.round((this.store.todayDone() / total) * 360) : 0;
    return `conic-gradient(var(--primary) ${deg}deg, var(--primary-soft) 0deg)`;
  });

  protected readonly leaderText = computed(() => {
    const top = [...(this.store.scoreboard()?.members ?? [])].sort(
      (a, b) => b.points - a.points,
    )[0];
    if (!top || top.points === 0) return 'Marca tus tareas para sumar puntos esta semana.';
    return `${this.family.member(top.userId).name} va a la cabeza: ${top.points} pts esta semana`;
  });

  /** Pendientes de hoy al domingo (atrasadas en «hoy»), agrupadas por día. */
  protected readonly week = computed<DayGroup[]>(() => {
    const tz = this.family.timezone();
    const today = this.store.today();
    const end = this.store.weekEnd();
    const groups = new Map<string, DayGroup>();
    for (const c of this.store.all()) {
      if (c.done && !this.store.isDoneToday(c)) continue;
      let key: string;
      if (this.store.isDoneToday(c)) key = today;
      else if (!c.dueAt) key = 'none';
      else {
        key = dayKey(c.dueAt, tz);
        if (key < today) key = today;
        if (key > end) continue;
      }
      if (!groups.has(key)) {
        const label =
          key === 'none'
            ? 'Sin fecha'
            : key === today
              ? 'Hoy'
              : key === addDays(today, 1)
                ? 'Mañana'
                : formatDay(`${key}T12:00:00Z`, 'UTC');
        groups.set(key, { key, label, chores: [] });
      }
      groups.get(key)!.chores.push(c);
    }
    return [...groups.values()].sort((a, b) =>
      a.key === 'none' ? 1 : b.key === 'none' ? -1 : a.key.localeCompare(b.key),
    );
  });

  protected readonly rotations = computed(() => this.store.all().filter((c) => c.rotation));

  constructor() {
    this.store.load().catch((e) => this.error.set(errorMessage(e)));
  }

  protected repeat = describeRepeat;

  protected day(instant: string) {
    return formatDay(instant, this.family.timezone());
  }

  protected isLate(c: Chore): boolean {
    return !!c.dueAt && new Date(c.dueAt).getTime() < Date.now();
  }

  protected meta(c: Chore): string {
    const tz = this.family.timezone();
    const parts: string[] = [];
    const rep = describeRepeat(c.rrule);
    if (rep) parts.push(rep);
    const done = this.store.completionToday(c);
    if (done) {
      parts.push(
        `hecha por ${this.family.member(done.completedBy).name} a las ${formatTime(done.completedAt, tz)}`,
      );
    } else if (c.dueAt) {
      const key = dayKey(c.dueAt, tz);
      const when =
        key === this.store.today()
          ? formatTime(c.dueAt, tz)
          : `${formatDay(c.dueAt, tz)} ${formatTime(c.dueAt, tz)}`;
      parts.push(this.isLate(c) ? `atrasada · ${when}` : when);
    }
    if (c.rotation) parts.push('rota');
    return parts.join(' · ');
  }

  /** Un hijo/a solo marca las suyas; los adultos, cualquiera. */
  protected canMark(c: Chore): boolean {
    return this.family.isAdult() || c.assigneeId === this.auth.profile()?.id;
  }

  async toggle(c: Chore, done: boolean) {
    this.busyId.set(c.id);
    this.error.set(null);
    try {
      if (done) await this.store.undo(c);
      else await this.store.complete(c);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busyId.set(null);
    }
  }
}

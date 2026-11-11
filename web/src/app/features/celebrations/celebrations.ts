import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatDate } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { Celebration } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { CelebrationForm, MONTHS } from './celebration-form';
import { CelebrationsStore } from './celebrations.store';

export function untilLabel(days: number | null): string {
  if (days === null) return '';
  if (days === 0) return '¡Hoy!';
  if (days === 1) return 'Mañana';
  return `En ${days} días`;
}

/** Cumpleaños y fechas especiales (RF-8) con ideas de regalo. */
@Component({
  selector: 'app-celebrations',
  imports: [RouterLink, Avatar, Icon, LiveBadge, CelebrationForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div class="col">
          <p class="muted">Recordatorios 7 días antes, un día antes y el mismo día</p>
          <h1>Cumpleaños</h1>
        </div>
        <span class="head-actions">
          <app-live-badge />
          <button class="btn" type="button" (click)="form().show(null)">
            <app-icon name="plus" [size]="18" /> Fecha
          </button>
        </span>
      </header>

      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }

      @if (!store.list()) {
        <div class="card empty">Cargando…</div>
      } @else {
        @if (next(); as n) {
          <button type="button" class="hero" (click)="form().show(n)">
            <span class="cake"><app-icon name="gift" [size]="30" /></span>
            <span class="txt">
              <span class="label">Próximo festejo · {{ untilLabel(n.daysUntil) }}</span>
              <b>{{ name(n) }}</b>
              <span>
                {{ when(n) }}
                @if (n.turning) {
                  · {{ age(n) }}
                }
              </span>
              @if (!n.giftIdeasHidden) {
                <span class="gifts"> {{ pendingGifts(n) }} idea(s) de regalo pendiente(s) </span>
              }
            </span>
            <span class="days">
              <b>{{ n.daysUntil }}</b>
              <span>{{ n.daysUntil === 1 ? 'día' : 'días' }}</span>
            </span>
          </button>
        }

        <section class="card list">
          @for (c of sorted(); track c.id ?? c.userId) {
            <button type="button" class="row" (click)="form().show(c)">
              @if (c.userId) {
                <app-avatar [member]="family.member(c.userId)" [size]="40" />
              } @else {
                <span class="dot" [class.ann]="c.type !== 'BIRTHDAY'">
                  <app-icon [name]="c.type === 'BIRTHDAY' ? 'gift' : 'star'" [size]="20" />
                </span>
              }
              <span class="body">
                <b>{{ name(c) }}</b>
                <span class="meta">
                  {{ when(c) }}
                  @if (c.turning) {
                    · {{ age(c) }}
                  }
                  @if (c.giftIdeas?.length) {
                    · {{ c.giftIdeas!.length }} idea(s)
                  }
                </span>
              </span>
              <span class="pill" [class.accent]="(c.daysUntil ?? 99) <= 7">
                {{ untilLabel(c.daysUntil) }}
              </span>
            </button>
          } @empty {
            <div class="empty">
              Aún no hay fechas. Los cumpleaños de los miembros aparecen solos cuando ponen su fecha
              en <a routerLink="/configuracion">Configuración</a>.
            </div>
          }
        </section>
        <p class="hint">
          Los cumpleaños de los miembros salen de su perfil. La persona festejada no ve sus ideas de
          regalo.
        </p>
      }
    </div>
    <app-celebration-form />
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
    }
    .hero {
      display: flex;
      align-items: center;
      gap: 18px;
      width: 100%;
      border: 0;
      border-radius: var(--r-hero);
      background: #6b4fa0;
      color: #fff;
      padding: 22px 24px;
      text-align: left;
      font: inherit;
      cursor: pointer;
    }
    .cake {
      width: 60px;
      height: 60px;
      flex: none;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.18);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .txt {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 3px;
      font-size: 14px;
    }
    .txt b {
      font-size: 22px;
      font-weight: 800;
    }
    .label,
    .gifts {
      opacity: 0.9;
    }
    .days {
      display: flex;
      flex-direction: column;
      align-items: center;
      font-size: 13px;
    }
    .days b {
      font-size: 36px;
      font-weight: 800;
      line-height: 1;
    }
    .list {
      padding: 6px;
      display: flex;
      flex-direction: column;
    }
    .row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px;
      border: 0;
      border-radius: 14px;
      background: none;
      text-align: left;
      font: inherit;
      color: inherit;
      cursor: pointer;
    }
    .row:hover {
      background: var(--muted);
    }
    .row + .row {
      border-top: 1px solid var(--divider);
    }
    .dot {
      width: 40px;
      height: 40px;
      flex: none;
      border-radius: 50%;
      background: #eee8f6;
      color: #6b4fa0;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .dot.ann {
      background: var(--accent-soft);
      color: var(--accent-strong);
    }
    .body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .meta {
      font-size: 13px;
      color: var(--text-2);
    }
    .empty {
      padding: 20px;
      color: var(--text-2);
    }
  `,
})
export class Celebrations {
  protected readonly family = inject(FamilyService);
  protected readonly store = inject(CelebrationsStore);
  protected readonly form = viewChild.required(CelebrationForm);
  protected readonly error = signal<string | null>(null);
  protected readonly untilLabel = untilLabel;

  protected readonly sorted = computed(() =>
    [...(this.store.list() ?? [])].sort((a, b) => (a.daysUntil ?? 999) - (b.daysUntil ?? 999)),
  );
  protected readonly next = computed(() => this.sorted().find((c) => c.daysUntil !== null) ?? null);

  constructor() {
    this.store.load().catch((e) => this.error.set(errorMessage(e)));
  }

  protected name(c: Celebration): string {
    if (c.type === 'BIRTHDAY') return c.personName ?? '';
    return c.type === 'ANNIVERSARY' ? `Aniversario: ${c.personName}` : (c.personName ?? '');
  }

  protected when(c: Celebration): string {
    if (c.nextDate)
      return formatDate(c.nextDate, { weekday: 'long', day: 'numeric', month: 'long' });
    return c.month && c.day ? `${c.day} de ${MONTHS[c.month - 1]}` : '';
  }

  protected age(c: Celebration): string {
    if (c.type === 'BIRTHDAY') return `cumple ${c.turning}`;
    return `${c.turning} ${c.turning === 1 ? 'año' : 'años'}`;
  }

  protected pendingGifts(c: Celebration): number {
    return (c.giftIdeas ?? []).filter((g) => !g.done).length;
  }
}

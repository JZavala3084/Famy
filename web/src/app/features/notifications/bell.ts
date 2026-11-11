import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { relativeTime } from '../../core/dates';
import { AppNotification, NotificationType } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { Icon, IconName } from '../../shared/icon';

export const TYPE_ICONS: Record<NotificationType, IconName> = {
  EVENT: 'calendar',
  PAYMENT: 'receipt',
  CELEBRATION: 'gift',
  SYSTEM: 'bell',
};

/** Campana de avisos: contador, panel con los últimos y un aviso flotante cuando llega uno en vivo. */
@Component({
  selector: 'app-bell',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.start]': "align() === 'start'",
    '(document:click)': 'outside($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
  template: `
    <button
      type="button"
      class="icon-btn bell"
      [attr.aria-label]="service.unread() ? 'Avisos, ' + service.unread() + ' sin leer' : 'Avisos'"
      [attr.aria-expanded]="open()"
      (click)="toggle()"
    >
      <app-icon name="bell" [size]="22" />
      @if (service.unread()) {
        <span class="badge">{{ service.unread() > 9 ? '9+' : service.unread() }}</span>
      }
    </button>

    @if (open()) {
      <div class="panel" role="dialog" aria-label="Avisos">
        <div class="head">
          <b>Avisos</b>
          @if (service.unread()) {
            <button class="link-btn" type="button" (click)="service.markAllRead()">
              Marcar todo como leído
            </button>
          }
        </div>
        <ul>
          @for (n of service.items(); track n.id) {
            <li>
              <button type="button" class="item" [class.unread]="!n.read" (click)="go(n)">
                <span class="ico"><app-icon [name]="icons[n.type]" [size]="18" /></span>
                <span class="txt">
                  <b>{{ n.title }}</b>
                  @if (n.body) {
                    <span>{{ n.body }}</span>
                  }
                  <small>{{ ago(n.createdAt) }}</small>
                </span>
              </button>
            </li>
          } @empty {
            <li class="empty">No tienes avisos.</li>
          }
        </ul>
      </div>
    }

    @if (toast(); as t) {
      <button type="button" class="toast" role="status" (click)="go(t)">
        <span class="ico"><app-icon [name]="icons[t.type]" [size]="18" /></span>
        <span class="txt">
          <b>{{ t.title }}</b>
          @if (t.body) {
            <span>{{ t.body }}</span>
          }
        </span>
      </button>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
    }
    .bell {
      position: relative;
    }
    .badge {
      position: absolute;
      top: 2px;
      right: 2px;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      border-radius: 999px;
      background: var(--accent);
      color: #fff;
      font-size: 11px;
      font-weight: 800;
      line-height: 18px;
      text-align: center;
    }
    :host(.start) .panel {
      /* En el menú lateral (que recorta lo que se sale): se abre hacia la derecha, fijo en pantalla. */
      position: fixed;
      top: 84px;
      left: 24px;
      right: auto;
    }
    .panel {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      z-index: 20;
      width: min(360px, calc(100vw - 24px));
      max-height: min(480px, 70dvh);
      overflow-y: auto;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--r-card);
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14);
    }
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 16px 8px;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0 6px 6px;
    }
    .item,
    .toast {
      display: flex;
      gap: 12px;
      width: 100%;
      padding: 10px;
      border: 0;
      border-radius: 12px;
      background: none;
      text-align: left;
      font: inherit;
      color: inherit;
      cursor: pointer;
    }
    .item:hover {
      background: var(--muted);
    }
    .item.unread {
      background: var(--primary-soft);
    }
    .ico {
      width: 34px;
      height: 34px;
      flex: none;
      border-radius: 10px;
      background: var(--muted);
      color: var(--text-3);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .unread .ico {
      background: var(--primary);
      color: #fff;
    }
    .txt {
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: 13px;
      min-width: 0;
    }
    .txt b {
      font-size: 14px;
    }
    small {
      color: var(--text-2);
    }
    .empty {
      padding: 16px 10px 20px;
      color: var(--text-2);
      font-size: 14px;
    }
    .toast {
      position: fixed;
      top: 16px;
      right: 16px;
      z-index: 30;
      width: min(360px, calc(100vw - 32px));
      background: var(--text);
      color: #fff;
      border-radius: var(--r-card);
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.25);
      animation: drop 0.25s ease-out;
    }
    .toast .ico {
      background: rgba(255, 255, 255, 0.15);
      color: #fff;
    }
    @keyframes drop {
      from {
        transform: translateY(-12px);
        opacity: 0;
      }
    }
  `,
})
export class Bell {
  protected readonly service = inject(NotificationsService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  /** `start` en el menú lateral; `end` (por omisión) en la barra superior del móvil. */
  readonly align = input<'start' | 'end'>('end');
  protected readonly icons = TYPE_ICONS;
  protected readonly open = signal(false);
  protected readonly toast = signal<AppNotification | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const n = this.service.latest();
      if (!n) return;
      untracked(() => {
        this.toast.set(n);
        clearTimeout(this.timer);
        this.timer = setTimeout(() => this.toast.set(null), 6000);
      });
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected toggle() {
    this.open.update((o) => !o);
    if (this.open()) this.service.load().catch(() => undefined);
  }

  protected outside(e: Event) {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) this.open.set(false);
  }

  protected ago(at: string) {
    return relativeTime(at);
  }

  protected go(n: AppNotification) {
    this.open.set(false);
    this.toast.set(null);
    this.service.markRead(n).catch(() => undefined);
    if (n.link) this.router.navigateByUrl(n.link);
  }
}

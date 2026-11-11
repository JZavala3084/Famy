import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FamilyService } from '../../core/family.service';
import { RealtimeService } from '../../core/realtime.service';
import { Avatar } from '../../shared/avatar';
import { Icon, IconName } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { Logo } from '../../shared/logo';

interface NavItem {
  label: string;
  icon: IconName;
  link: string;
  soon?: boolean;
}

export const NAV: NavItem[] = [
  { label: 'Inicio', icon: 'home', link: '/' },
  { label: 'Compras', icon: 'cart', link: '/compras' },
  { label: 'Tareas', icon: 'tasks', link: '/tareas' },
  { label: 'Presupuesto', icon: 'wallet', link: '/pronto/presupuesto', soon: true },
  { label: 'Recetas', icon: 'book', link: '/pronto/recetas', soon: true },
  { label: 'Horarios', icon: 'clock', link: '/pronto/horarios', soon: true },
  { label: 'Pagos', icon: 'receipt', link: '/pronto/pagos', soon: true },
  { label: 'Cumpleaños', icon: 'gift', link: '/pronto/cumpleanos', soon: true },
  { label: 'Salud', icon: 'pill', link: '/pronto/salud', soon: true },
  { label: 'Familia', icon: 'family', link: '/familia' },
];

const TABS: NavItem[] = [
  { label: 'Inicio', icon: 'home', link: '/' },
  { label: 'Compras', icon: 'cart', link: '/compras' },
  { label: 'Tareas', icon: 'tasks', link: '/tareas' },
  { label: 'Familia', icon: 'family', link: '/familia' },
  { label: 'Ajustes', icon: 'settings', link: '/configuracion' },
];

/** Panel familiar: menú lateral en escritorio, barra inferior en móvil (09 · Diseño UI). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Logo, Avatar, LiveBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <aside class="side">
      <a class="brand" routerLink="/">
        <app-logo [size]="40" />
        <span>
          <b>Famy</b>
          <span class="muted small">{{ family.current()?.name }}</span>
        </span>
      </a>
      <nav aria-label="Secciones">
        @for (n of nav; track n.link) {
          <a
            [routerLink]="n.link"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: n.link === '/' }"
            [class.soon]="n.soon"
          >
            <app-icon [name]="n.icon" />
            <span class="grow">{{ n.label }}</span>
            @if (n.soon) {
              <span class="tag">Pronto</span>
            }
          </a>
        }
      </nav>
      <div class="me">
        @if (me(); as m) {
          <a routerLink="/configuracion" routerLinkActive="active" class="me-link">
            <app-avatar [member]="m" [size]="32" />
            <span class="grow ellipsis">{{ m.name }}</span>
            <app-icon name="settings" [size]="18" />
          </a>
        }
        <button type="button" class="logout" (click)="logout()">
          <app-icon name="logout" [size]="18" /> Cerrar sesión
        </button>
      </div>
    </aside>

    <header class="top">
      <a class="brand" routerLink="/">
        <app-logo [size]="34" />
        <b>{{ family.current()?.name || 'Famy' }}</b>
      </a>
      <app-live-badge />
    </header>

    <main id="contenido" class="content" tabindex="-1">
      <router-outlet />
    </main>

    <nav class="tabs" aria-label="Secciones principales">
      @for (t of tabs; track t.link) {
        <a
          [routerLink]="t.link"
          routerLinkActive="active"
          [routerLinkActiveOptions]="{ exact: t.link === '/' }"
        >
          <app-icon [name]="t.icon" [size]="22" />
          <span>{{ t.label }}</span>
        </a>
      }
    </nav>
  `,
  styles: `
    :host {
      display: grid;
      grid-template-columns: 264px minmax(0, 1fr);
      min-height: 100dvh;
    }
    .skip {
      position: absolute;
      left: -999px;
    }
    .skip:focus {
      left: 16px;
      top: 16px;
      z-index: 10;
      background: var(--surface);
      padding: 8px 12px;
      border-radius: 8px;
    }
    .side {
      position: sticky;
      top: 0;
      height: 100dvh;
      overflow-y: auto;
      background: var(--surface);
      border-right: 1px solid var(--border);
      padding: 24px 16px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 8px;
      color: inherit;
      text-decoration: none;
    }
    .brand > span {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .brand b {
      font-size: 18px;
      font-weight: 800;
    }
    nav[aria-label='Secciones'] {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
    }
    nav[aria-label='Secciones'] a,
    .me-link,
    .logout {
      height: 44px;
      border-radius: 12px;
      padding: 0 12px;
      display: flex;
      align-items: center;
      gap: 12px;
      text-decoration: none;
      font-size: 14px;
      font-weight: 500;
      color: var(--text-3);
    }
    nav[aria-label='Secciones'] a:hover,
    .me-link:hover,
    .logout:hover {
      background: var(--muted);
      color: var(--text);
    }
    nav[aria-label='Secciones'] a.active,
    .me-link.active {
      background: var(--primary-soft);
      color: var(--primary);
      font-weight: 700;
    }
    a.soon {
      color: #8b909a;
    }
    .tag {
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 999px;
      background: var(--muted);
      color: var(--text-2);
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .ellipsis {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .me {
      display: flex;
      flex-direction: column;
      gap: 2px;
      border-top: 1px solid var(--border);
      padding-top: 12px;
    }
    .logout {
      border: 0;
      background: none;
      cursor: pointer;
      width: 100%;
    }
    .content {
      min-width: 0;
      padding: 28px clamp(16px, 3vw, 40px) 40px;
      max-width: 1240px;
      width: 100%;
      outline: none;
    }
    .top,
    .tabs {
      display: none;
    }

    @media (max-width: 860px) {
      :host {
        display: block;
        padding-bottom: calc(76px + env(safe-area-inset-bottom));
      }
      .side {
        display: none;
      }
      .top {
        display: flex;
        position: sticky;
        top: 0;
        z-index: 5;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        padding: 12px 16px;
        background: color-mix(in srgb, var(--bg) 92%, transparent);
        backdrop-filter: blur(8px);
      }
      .top .brand {
        padding: 0;
        min-width: 0;
      }
      .top .brand b {
        font-size: 16px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .content {
        padding: 8px 16px 24px;
      }
      .tabs {
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        position: fixed;
        inset: auto 0 0 0;
        z-index: 5;
        background: var(--surface);
        border-top: 1px solid var(--border);
        padding: 8px 4px calc(8px + env(safe-area-inset-bottom));
      }
      .tabs a {
        min-height: 52px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 4px;
        font-size: 11px;
        font-weight: 600;
        color: var(--text-2);
        text-decoration: none;
        border-radius: 12px;
      }
      .tabs a.active {
        color: var(--primary);
        font-weight: 700;
      }
    }
  `,
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly realtime = inject(RealtimeService);
  private readonly router = inject(Router);
  protected readonly family = inject(FamilyService);
  protected readonly nav = NAV;
  protected readonly tabs = TABS;
  protected readonly me = computed(() => {
    const id = this.auth.profile()?.id;
    return id ? this.family.member(id) : null;
  });

  constructor() {
    effect(() => {
      const id = this.family.current()?.id;
      if (id) this.realtime.start(id);
    });
    inject(DestroyRef).onDestroy(() => this.realtime.stop());
  }

  async logout() {
    this.realtime.stop();
    await this.auth.logout();
    this.family.clear();
    await this.router.navigateByUrl('/login');
  }
}

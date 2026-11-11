import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FamilyService } from '../../core/family.service';
import { Icon } from '../../shared/icon';
import { NAV, Shell, TABS } from '../shell/shell';

/** «Más» en móvil: las secciones que no caben en la barra inferior. */
@Component({
  selector: 'app-more',
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page narrow">
      <header class="page-head"><h1>Más</h1></header>
      <nav class="card list" aria-label="Más secciones">
        @for (n of items(); track n.link) {
          <a [routerLink]="n.link" [class.soon]="n.soon">
            <span class="ico"><app-icon [name]="n.icon" [size]="20" /></span>
            <span class="grow">{{ n.label }}</span>
            @if (n.soon) {
              <span class="pill">Pronto</span>
            } @else {
              <app-icon name="next" [size]="18" />
            }
          </a>
        }
        <a routerLink="/configuracion">
          <span class="ico"><app-icon name="settings" [size]="20" /></span>
          <span class="grow">Configuración</span>
          <app-icon name="next" [size]="18" />
        </a>
      </nav>
      <button class="btn ghost block" type="button" (click)="shell.logout()">
        <app-icon name="logout" [size]="18" /> Cerrar sesión
      </button>
    </div>
  `,
  styles: `
    .list {
      padding: 6px;
      display: flex;
      flex-direction: column;
    }
    a {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 52px;
      padding: 6px 10px;
      border-radius: 12px;
      color: inherit;
      text-decoration: none;
      font-weight: 600;
    }
    a + a {
      border-top: 1px solid var(--divider);
    }
    a:hover {
      background: var(--muted);
    }
    a.soon {
      color: var(--text-2);
    }
    .ico {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: var(--primary-soft);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .soon .ico {
      background: var(--muted);
      color: var(--text-2);
    }
    .grow {
      flex: 1;
    }
  `,
})
export class More {
  private readonly family = inject(FamilyService);
  protected readonly shell = inject(Shell);
  protected readonly items = computed(() => {
    const inTabs = new Set(TABS.map((t) => t.link));
    return NAV.filter((n) => !inTabs.has(n.link) && (!n.adults || this.family.isAdult()));
  });
}

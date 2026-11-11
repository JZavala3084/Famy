import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';

/** Marco común de las pantallas de acceso (diseño móvil centrado también en escritorio). */
@Component({
  selector: 'app-auth-card',
  imports: [Logo, Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="wrap">
      <div class="card-auth fade-in">
        @if (backLink()) {
          <a class="icon-btn back" [routerLink]="backLink()" aria-label="Volver">
            <app-icon name="back" [size]="22" />
          </a>
        } @else {
          <app-logo [size]="56" />
        }
        <header>
          <h1>{{ heading() }}</h1>
          @if (subtitle()) {
            <p class="muted">{{ subtitle() }}</p>
          }
        </header>
        <ng-content />
      </div>
    </main>
  `,
  styles: `
    .wrap {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 32px 16px;
    }
    .card-auth {
      width: 100%;
      max-width: 440px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    @media (min-width: 640px) {
      .card-auth {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--r-hero);
        padding: 36px 32px;
      }
    }
    header {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .back {
      margin-left: -10px;
    }
  `,
})
export class AuthCard {
  readonly heading = input.required<string>();
  readonly subtitle = input<string>();
  readonly backLink = input<string>();
}

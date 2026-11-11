import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FamilyService, MEMBER_COLORS } from '../../core/family.service';
import { errorMessage } from '../../core/problem';
import { RealtimeService } from '../../core/realtime.service';
import { Icon } from '../../shared/icon';
import { PasswordStrength, passwordOk } from '../auth/password-strength';
import { NotificationSettings } from '../notifications/notification-settings';
import { CURRENCIES } from '../onboarding/onboarding';
import { NotificationsService } from '../../core/notifications.service';

const TIMEZONES: string[] = (() => {
  try {
    return (Intl as unknown as { supportedValuesOf(k: string): string[] }).supportedValuesOf(
      'timeZone',
    );
  } catch {
    return [];
  }
})();

/** Configuración (prototipo «Configuracion»): perfil, moneda, zona horaria y seguridad. */
@Component({
  selector: 'app-settings',
  imports: [FormsModule, Icon, PasswordStrength, NotificationSettings],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page narrow">
      <header class="page-head"><h1>Configuración</h1></header>

      @if (message(); as m) {
        <div class="alert" [class.info]="m.ok" [class.error]="!m.ok" role="status">
          {{ m.text }}
        </div>
      }

      <section class="card">
        <form class="form" (ngSubmit)="saveProfile()">
          <h2>Perfil</h2>
          <div class="field">
            <label for="s-name">Nombre</label>
            <input
              id="s-name"
              class="input"
              name="name"
              maxlength="80"
              [(ngModel)]="p.displayName"
            />
          </div>
          <div class="field">
            <span class="label">Tu color</span>
            <div class="colors" role="radiogroup" aria-label="Color">
              @for (c of colors; track c) {
                <button
                  type="button"
                  role="radio"
                  class="swatch"
                  [style.background]="c"
                  [attr.aria-checked]="p.color === c"
                  [attr.aria-label]="c"
                  (click)="p.color = c"
                >
                  @if (p.color === c) {
                    <app-icon name="check" [size]="18" [stroke]="3" />
                  }
                </button>
              }
            </div>
          </div>
          <div class="field">
            <label for="s-bday">Cumpleaños</label>
            <input id="s-bday" class="input" name="birthday" type="date" [(ngModel)]="p.birthday" />
          </div>
          <div class="field">
            <label for="s-cur">Moneda</label>
            <select id="s-cur" class="input" name="currency" [(ngModel)]="p.currency">
              @for (c of currencies; track c[0]) {
                <option [value]="c[0]">{{ c[0] }} · {{ c[1] }}</option>
              }
            </select>
            <p class="hint">Vista previa: {{ preview() }}</p>
          </div>
          <div class="field">
            <label for="s-tz">Zona horaria</label>
            @if (timezones.length) {
              <select id="s-tz" class="input" name="tz" [(ngModel)]="p.timezone">
                @for (tz of timezones; track tz) {
                  <option [value]="tz">{{ tz }}</option>
                }
              </select>
            } @else {
              <input id="s-tz" class="input" name="tz" [(ngModel)]="p.timezone" />
            }
          </div>
          <div class="field">
            <span class="label">Correo</span>
            <p>
              {{ auth.profile()?.email }}
              @if (auth.profile()?.emailVerified) {
                <span class="pill primary">Confirmado</span>
              } @else {
                <span class="pill accent">Sin confirmar</span>
              }
            </p>
          </div>
          <div class="actions">
            <button class="btn" type="submit" [disabled]="busy()">Guardar perfil</button>
          </div>
        </form>
      </section>

      <section class="card">
        <form class="form" (ngSubmit)="changePassword()">
          <h2>{{ auth.profile()?.hasPassword ? 'Cambiar contraseña' : 'Crear contraseña' }}</h2>
          @if (!auth.profile()?.hasPassword) {
            <p class="hint">
              Entras con Google. Si creas una contraseña también podrás entrar con tu correo.
            </p>
          }
          @if (auth.profile()?.hasPassword) {
            <div class="field">
              <label for="s-cur-pw">Contraseña actual</label>
              <input
                id="s-cur-pw"
                class="input"
                name="current"
                type="password"
                autocomplete="current-password"
                [(ngModel)]="pw.current"
              />
            </div>
          }
          <div class="field">
            <label for="s-new-pw">Contraseña nueva</label>
            <input
              id="s-new-pw"
              class="input"
              name="next"
              type="password"
              autocomplete="new-password"
              [ngModel]="pw.next"
              (ngModelChange)="pw.next = $event; nextPw.set($event)"
            />
            <app-password-strength [password]="nextPw()" />
          </div>
          <p class="hint">Se cerrará la sesión en tus otros dispositivos.</p>
          <div class="actions">
            <button class="btn" type="submit" [disabled]="busy()">Guardar contraseña</button>
          </div>
        </form>
      </section>

      <app-notification-settings />

      <section class="card">
        <h2>Sesiones</h2>
        <div class="actions start">
          <button class="btn ghost" type="button" (click)="logout(false)">
            <app-icon name="logout" [size]="18" /> Cerrar sesión
          </button>
          <button class="btn ghost" type="button" (click)="logout(true)">
            Cerrar sesión en todos los dispositivos
          </button>
        </div>
      </section>
    </div>
  `,
  styles: `
    .narrow {
      max-width: 680px;
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .colors {
      display: flex;
      gap: 10px;
    }
    .swatch {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: 0;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .swatch[aria-checked='true'] {
      box-shadow:
        0 0 0 3px var(--surface),
        0 0 0 5px var(--text);
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      flex-wrap: wrap;
    }
    .actions.start {
      justify-content: flex-start;
      margin-top: 12px;
    }
  `,
})
export class Settings {
  protected readonly auth = inject(AuthService);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);
  private readonly notifications = inject(NotificationsService);
  private readonly router = inject(Router);

  protected readonly colors = MEMBER_COLORS;
  protected readonly currencies = CURRENCIES;
  protected readonly timezones = TIMEZONES;
  protected readonly busy = signal(false);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly nextPw = signal('');
  protected p = { displayName: '', color: '', birthday: '', currency: 'USD', timezone: '' };
  protected pw = { current: '', next: '' };
  private readonly currency = signal('USD');
  protected readonly preview = computed(() => {
    try {
      return new Intl.NumberFormat('es', { style: 'currency', currency: this.currency() }).format(
        1234.5,
      );
    } catch {
      return '';
    }
  });

  constructor() {
    effect(() => {
      const prof = this.auth.profile();
      if (!prof) return;
      untracked(() => {
        this.p = {
          displayName: prof.displayName,
          color: prof.color ?? '',
          birthday: prof.birthday ?? '',
          currency: prof.currency,
          timezone: prof.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
        };
        this.currency.set(prof.currency);
      });
    });
  }

  async saveProfile() {
    await this.run('Perfil guardado.', async () => {
      await this.auth.updateProfile({
        displayName: this.p.displayName.trim() || undefined,
        color: this.p.color || undefined,
        birthday: this.p.birthday || undefined,
        currency: this.p.currency,
        timezone: this.p.timezone || undefined,
      });
      this.currency.set(this.p.currency);
      // Nombre y color también se ven en la familia.
      await this.family.reloadCurrent();
    });
  }

  async changePassword() {
    if (!passwordOk(this.pw.next)) {
      this.message.set({ ok: false, text: 'La contraseña nueva no cumple las reglas.' });
      return;
    }
    await this.run('Contraseña actualizada.', async () => {
      await this.auth.changePassword(this.pw.current || null, this.pw.next);
      this.pw = { current: '', next: '' };
      this.nextPw.set('');
    });
  }

  async logout(everywhere: boolean) {
    await this.notifications.forgetBrowser().catch(() => undefined);
    this.notifications.clear();
    this.realtime.stop();
    await this.auth.logout(everywhere);
    this.family.clear();
    await this.router.navigateByUrl('/login');
  }

  private async run(ok: string, action: () => Promise<void>) {
    this.busy.set(true);
    this.message.set(null);
    try {
      await action();
      this.message.set({ ok: true, text: ok });
    } catch (e) {
      this.message.set({ ok: false, text: errorMessage(e) });
    } finally {
      this.busy.set(false);
    }
  }
}

import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/problem';
import { Icon } from '../../shared/icon';
import { AuthCard } from './auth-card';
import { GoogleButton } from './google-button';

@Component({
  selector: 'app-login',
  imports: [AuthCard, ReactiveFormsModule, RouterLink, GoogleButton, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-card
      heading="Bienvenido a Famy"
      subtitle="Compras, tareas, pagos y fechas de tu familia en un solo lugar."
    >
      <form [formGroup]="form" (ngSubmit)="submit()" class="form" novalidate>
        @if (error()) {
          <div class="alert error" role="alert">{{ error() }}</div>
        }
        <div class="field">
          <label for="email">Correo electrónico</label>
          <input
            id="email"
            class="input"
            type="email"
            autocomplete="email"
            placeholder="tu@correo.com"
            formControlName="email"
          />
        </div>
        <div class="field">
          <div class="row">
            <label for="pass" class="label">Contraseña</label>
            <a routerLink="/olvide-contrasena" class="small">¿La olvidaste?</a>
          </div>
          <input
            id="pass"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="••••••••"
            formControlName="password"
          />
        </div>
        <button class="btn block" type="submit" [disabled]="busy()">
          {{ busy() ? 'Entrando…' : 'Iniciar sesión' }}
        </button>
      </form>
      <app-google-button (credential)="google($event)" />
      <footer>
        <p>¿No tienes cuenta? <a routerLink="/registro">Regístrate</a></p>
        <p class="muted small secure">
          <app-icon name="shield" [size]="16" /> Conexión cifrada · contraseñas protegidas con
          Argon2
        </p>
      </footer>
    </app-auth-card>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
    }
    footer {
      display: flex;
      flex-direction: column;
      gap: 10px;
      align-items: center;
      text-align: center;
      font-size: 14px;
    }
    .secure {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly returnUrl = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  async submit() {
    if (this.form.invalid) {
      this.error.set('Escribe tu correo y tu contraseña.');
      return;
    }
    const { email, password } = this.form.getRawValue();
    await this.run(() => this.auth.login(email.trim(), password));
  }

  async google(idToken: string) {
    await this.run(() => this.auth.loginWithGoogle(idToken));
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      await this.router.navigateByUrl(safeReturn(this.returnUrl()));
    } catch (e) {
      this.error.set(errorMessage(e, 'Correo o contraseña incorrectos.'));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Solo rutas internas, para no redirigir a otro sitio. */
export function safeReturn(url: string | undefined | null): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}

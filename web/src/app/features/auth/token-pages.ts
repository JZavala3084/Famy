import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/problem';
import { AuthCard } from './auth-card';
import { PasswordStrength, passwordOk } from './password-strength';
import { toSignal } from '@angular/core/rxjs-interop';

/** `/verify-email?token=…` (enlace del correo de verificación). */
@Component({
  selector: 'app-verify-email',
  imports: [AuthCard, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (state()) {
      @case ('working') {
        <app-auth-card heading="Confirmando tu correo…" />
      }
      @case ('ok') {
        <app-auth-card
          heading="¡Correo confirmado!"
          subtitle="Ya puedes crear tu familia o unirte a una."
        >
          <a class="btn block" [routerLink]="auth.isLoggedIn() ? '/' : '/login'">Continuar</a>
        </app-auth-card>
      }
      @case ('error') {
        <app-auth-card heading="No pudimos confirmar tu correo" [subtitle]="error()">
          <a class="btn block" routerLink="/login">Ir a iniciar sesión</a>
        </app-auth-card>
      }
    }
  `,
})
export class VerifyEmail implements OnInit {
  protected readonly auth = inject(AuthService);
  readonly token = input<string>();
  protected readonly state = signal<'working' | 'ok' | 'error'>('working');
  protected readonly error = signal('');

  async ngOnInit() {
    const token = this.token();
    if (!token) {
      this.error.set('El enlace está incompleto.');
      this.state.set('error');
      return;
    }
    try {
      await this.auth.verifyEmail(token);
      if (this.auth.isLoggedIn()) await this.auth.loadProfile().catch(() => undefined);
      this.state.set('ok');
    } catch (e) {
      this.error.set(errorMessage(e, 'El enlace venció o ya se usó. Pide uno nuevo.'));
      this.state.set('error');
    }
  }
}

/** `/olvide-contrasena`. */
@Component({
  selector: 'app-forgot-password',
  imports: [AuthCard, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-card
      heading="Recupera tu contraseña"
      subtitle="Te enviaremos un enlace para crear una nueva."
      backLink="/login"
    >
      @if (sent()) {
        <div class="alert info">
          Si existe una cuenta con ese correo, te llegará un enlace en unos minutos.
        </div>
      } @else {
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
              formControlName="email"
            />
          </div>
          <button class="btn block" type="submit" [disabled]="busy()">Enviar enlace</button>
        </form>
      }
    </app-auth-card>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
  `,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });
  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  async submit() {
    if (this.form.invalid) {
      this.error.set('Escribe un correo válido.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.forgotPassword(this.form.getRawValue().email.trim());
      this.sent.set(true);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

/** `/reset-password?token=…` (enlace del correo de recuperación). */
@Component({
  selector: 'app-reset-password',
  imports: [AuthCard, ReactiveFormsModule, PasswordStrength, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-card heading="Nueva contraseña" backLink="/login">
      @if (done()) {
        <div class="alert info">Listo. Inicia sesión con tu nueva contraseña.</div>
        <a class="btn block" routerLink="/login">Iniciar sesión</a>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="form" novalidate>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <div class="field">
            <label for="pw">Contraseña nueva</label>
            <input
              id="pw"
              class="input"
              type="password"
              autocomplete="new-password"
              formControlName="password"
            />
            <app-password-strength [password]="password() ?? ''" />
          </div>
          <button class="btn block" type="submit" [disabled]="busy()">Guardar contraseña</button>
        </form>
      }
    </app-auth-card>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
  `,
})
export class ResetPassword {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly token = input<string>();
  protected readonly form = inject(FormBuilder).nonNullable.group({ password: [''] });
  protected readonly password = toSignal(this.form.controls.password.valueChanges);
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  async submit() {
    const pw = this.form.getRawValue().password;
    if (!passwordOk(pw)) {
      this.error.set('La contraseña no cumple las reglas.');
      return;
    }
    const token = this.token();
    if (!token) {
      this.error.set('El enlace está incompleto. Pide uno nuevo.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.resetPassword(token, pw);
      this.auth.clear();
      this.done.set(true);
    } catch (e) {
      this.error.set(errorMessage(e, 'El enlace venció o ya se usó. Pide uno nuevo.'));
    } finally {
      this.busy.set(false);
    }
  }
}

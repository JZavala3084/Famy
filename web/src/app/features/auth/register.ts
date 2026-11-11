import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/problem';
import { AuthCard } from './auth-card';
import { GoogleButton } from './google-button';
import { PasswordStrength, passwordOk } from './password-strength';

@Component({
  selector: 'app-register',
  imports: [AuthCard, ReactiveFormsModule, RouterLink, GoogleButton, PasswordStrength],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sentTo(); as email) {
      <app-auth-card heading="Revisa tu correo" backLink="/login">
        <div class="alert info">
          Te enviamos un enlace a <b>{{ email }}</b> para confirmar tu cuenta. Ya puedes iniciar
          sesión; para crear o unirte a una familia necesitas confirmar el correo.
        </div>
        <a class="btn block" routerLink="/login">Ir a iniciar sesión</a>
        <p class="small muted center">
          ¿No llegó?
          <button class="link-btn" type="button" (click)="resend(email)" [disabled]="busy()">
            Reenviar
          </button>
          @if (resent()) {
            · Enviado
          }
        </p>
      </app-auth-card>
    } @else {
      <app-auth-card
        heading="Crea tu cuenta"
        subtitle="Después podrás crear tu familia o unirte a una."
        backLink="/login"
      >
        <form [formGroup]="form" (ngSubmit)="submit()" class="form" novalidate>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <div class="field">
            <label for="nombre">Nombre</label>
            <input
              id="nombre"
              class="input"
              autocomplete="name"
              placeholder="Tu nombre"
              formControlName="name"
            />
          </div>
          <div class="field">
            <label for="correo">Correo electrónico</label>
            <input
              id="correo"
              class="input"
              type="email"
              autocomplete="email"
              placeholder="tu@correo.com"
              formControlName="email"
            />
          </div>
          <div class="field">
            <label for="clave">Contraseña</label>
            <input
              id="clave"
              class="input"
              type="password"
              autocomplete="new-password"
              formControlName="password"
            />
            <app-password-strength [password]="password() ?? ''" />
          </div>
          <label class="terms">
            <input type="checkbox" formControlName="terms" />
            Acepto los términos y el aviso de privacidad de Famy.
          </label>
          <button class="btn block" type="submit" [disabled]="busy()">
            {{ busy() ? 'Creando…' : 'Crear cuenta' }}
          </button>
        </form>
        <app-google-button text="signup_with" (credential)="google($event)" />
      </app-auth-card>
    }
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .terms {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      font-size: 14px;
    }
    .terms input {
      width: 18px;
      height: 18px;
      margin: 1px 0 0;
      accent-color: var(--primary);
    }
    .center {
      text-align: center;
    }
  `,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
    terms: [false, Validators.requiredTrue],
  });
  protected readonly password = toSignal(this.form.controls.password.valueChanges);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);
  protected readonly resent = signal(false);

  async submit() {
    const v = this.form.getRawValue();
    if (!v.name.trim() || this.form.controls.email.invalid) {
      this.error.set('Escribe tu nombre y un correo válido.');
      return;
    }
    if (!passwordOk(v.password)) {
      this.error.set('La contraseña no cumple las reglas.');
      return;
    }
    if (!v.terms) {
      this.error.set('Acepta los términos para continuar.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.register(v.name.trim(), v.email.trim(), v.password);
      this.sentTo.set(v.email.trim());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  async resend(email: string) {
    this.busy.set(true);
    try {
      await this.auth.resendVerification(email);
      this.resent.set(true);
    } finally {
      this.busy.set(false);
    }
  }

  async google(idToken: string) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.loginWithGoogle(idToken);
      await this.router.navigateByUrl('/');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

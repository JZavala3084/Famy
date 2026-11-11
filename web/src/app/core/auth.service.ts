import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from './config';
import { Profile, TokenResponse } from './models';

interface Claims {
  sub: string;
  exp: number;
  fid?: string;
  role?: string;
}

/** Margen antes del vencimiento para renovar el access token por adelantado. */
const REFRESH_MARGIN_MS = 60_000;

export function decodeJwt(token: string): Claims | null {
  try {
    const part = token.split('.')[1];
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decodeURIComponent(escape(json))) as Claims;
  } catch {
    return null;
  }
}

/**
 * Sesión web: el access token (15 min) vive solo en memoria; el refresh token va en la cookie HttpOnly
 * `famy_rt`, que el navegador manda solo a `/api/v1/auth`. Al recargar la página se recupera la sesión con
 * `/auth/refresh`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);

  private readonly token = signal<string | null>(null);
  private readonly claims = computed(() => {
    const t = this.token();
    return t ? decodeJwt(t) : null;
  });
  private refreshing: Promise<string | null> | null = null;

  readonly profile = signal<Profile | null>(null);
  readonly isLoggedIn = computed(() => this.token() !== null);
  /** Familia activa según el token (claim `fid`). */
  readonly familyId = computed(() => this.claims()?.fid ?? null);

  get accessToken(): string | null {
    return this.token();
  }

  /** Intenta recuperar la sesión con la cookie al arrancar la app (solo si hubo una sesión en este navegador). */
  async restore(): Promise<void> {
    if (!hadSession()) return;
    if (await this.refresh()) {
      await this.loadProfile().catch(() => undefined);
    }
  }

  async login(email: string, password: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<TokenResponse>(this.config.api('/auth/login'), {
        email,
        password,
        deviceName: deviceName(),
      }),
    );
    await this.startSession(res);
  }

  async loginWithGoogle(idToken: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<TokenResponse>(this.config.api('/auth/google'), {
        idToken,
        deviceName: deviceName(),
      }),
    );
    await this.startSession(res);
  }

  register(displayName: string, email: string, password: string): Promise<unknown> {
    return firstValueFrom(
      this.http.post(this.config.api('/auth/register'), {
        displayName,
        email,
        password,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        locale: 'es',
      }),
    );
  }

  verifyEmail(token: string): Promise<unknown> {
    return firstValueFrom(this.http.post(this.config.api('/auth/verify-email'), { token }));
  }

  resendVerification(email: string): Promise<unknown> {
    return firstValueFrom(this.http.post(this.config.api('/auth/resend-verification'), { email }));
  }

  forgotPassword(email: string): Promise<unknown> {
    return firstValueFrom(this.http.post(this.config.api('/auth/password/forgot'), { email }));
  }

  resetPassword(token: string, newPassword: string): Promise<unknown> {
    return firstValueFrom(
      this.http.post(this.config.api('/auth/password/reset'), { token, newPassword }),
    );
  }

  async changePassword(currentPassword: string | null, newPassword: string): Promise<void> {
    await firstValueFrom(
      this.http.post(this.config.api('/auth/password/change'), { currentPassword, newPassword }),
    );
    // El backend cierra las demás sesiones y borra la cookie: se pide una sesión nueva con la contraseña nueva.
    const email = this.profile()?.email;
    if (email) {
      await this.login(email, newPassword);
    }
  }

  async loadProfile(): Promise<Profile> {
    const p = await firstValueFrom(this.http.get<Profile>(this.config.api('/me')));
    this.profile.set(p);
    return p;
  }

  async updateProfile(changes: Partial<Profile>): Promise<Profile> {
    const p = await firstValueFrom(this.http.patch<Profile>(this.config.api('/me'), changes));
    this.profile.set(p);
    return p;
  }

  /** Devuelve un access token vigente, renovándolo si está por vencer. */
  async freshToken(): Promise<string | null> {
    const t = this.token();
    const exp = this.claims()?.exp;
    if (t && exp && exp * 1000 - Date.now() > REFRESH_MARGIN_MS) {
      return t;
    }
    return t || this.refreshing ? this.refresh() : null;
  }

  /** Rota el refresh token (cookie). Las llamadas simultáneas comparten la misma petición. */
  refresh(): Promise<string | null> {
    if (!this.refreshing) {
      this.refreshing = firstValueFrom(
        this.http.post<TokenResponse>(this.config.api('/auth/refresh'), {}),
      )
        .then((res) => {
          this.token.set(res.accessToken);
          return res.accessToken;
        })
        .catch(() => {
          this.clear();
          return null;
        })
        .finally(() => (this.refreshing = null));
    }
    return this.refreshing;
  }

  async logout(everywhere = false): Promise<void> {
    try {
      await firstValueFrom(
        this.http.post(this.config.api(everywhere ? '/auth/logout-all' : '/auth/logout'), {}),
      );
    } catch {
      // Aunque falle, se cierra la sesión local.
    }
    this.clear();
  }

  clear(): void {
    this.token.set(null);
    this.profile.set(null);
    markSession(false);
  }

  private async startSession(res: TokenResponse): Promise<void> {
    this.token.set(res.accessToken);
    markSession(true);
    await this.loadProfile();
  }
}

/**
 * Marca (no secreta) de que este navegador tiene una sesión: evita pedir `/auth/refresh` —y un 401 en la consola—
 * a quien nunca inició sesión. El refresh token sigue solo en la cookie HttpOnly.
 */
const SESSION_FLAG = 'famy_session';

function hadSession(): boolean {
  try {
    return localStorage.getItem(SESSION_FLAG) === '1';
  } catch {
    return true;
  }
}

function markSession(on: boolean): void {
  try {
    if (on) localStorage.setItem(SESSION_FLAG, '1');
    else localStorage.removeItem(SESSION_FLAG);
  } catch {
    // Sin almacenamiento se intenta siempre la renovación.
  }
}

function deviceName(): string {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /Firefox\//.test(ua)
      ? 'Firefox'
      : /Chrome\//.test(ua)
        ? 'Chrome'
        : /Safari\//.test(ua)
          ? 'Safari'
          : 'Navegador';
  return `Web · ${browser}`;
}

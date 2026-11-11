import { Injectable } from '@angular/core';

/**
 * Configuración en tiempo de ejecución, leída de `/config.json` al arrancar. Así la misma imagen Docker sirve
 * para cualquier entorno (el contenedor genera el archivo a partir de variables de entorno).
 */
export interface RuntimeConfig {
  /** Origen de la API. Vacío = mismo origen (Caddy en producción, proxy de `ng serve` en desarrollo). */
  apiUrl: string;
  /** Client ID web de Google Identity Services. Vacío = se oculta «Continuar con Google». */
  googleClientId: string;
}

const DEFAULTS: RuntimeConfig = { apiUrl: '', googleClientId: '' };

@Injectable({ providedIn: 'root' })
export class AppConfig {
  private value: RuntimeConfig = DEFAULTS;

  async load(): Promise<void> {
    try {
      const res = await fetch('/config.json', { cache: 'no-store' });
      if (res.ok) {
        this.value = { ...DEFAULTS, ...((await res.json()) as Partial<RuntimeConfig>) };
      }
    } catch {
      // Sin config.json se usan los valores por defecto.
    }
  }

  get apiUrl(): string {
    return this.value.apiUrl.replace(/\/$/, '');
  }

  get googleClientId(): string {
    return this.value.googleClientId;
  }

  api(path: string): string {
    return `${this.apiUrl}/api/v1${path}`;
  }

  /** URL del WebSocket STOMP (`/ws`) con el esquema ws/wss correcto. */
  wsUrl(): string {
    const base = this.apiUrl || window.location.origin;
    return base.replace(/^http/, 'ws') + '/ws';
  }
}

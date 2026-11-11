import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { AppConfig } from '../../core/config';

interface GoogleId {
  initialize(opts: object): void;
  renderButton(el: HTMLElement, opts: object): void;
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

let gisLoading: Promise<void> | null = null;

function loadGis(): Promise<void> {
  gisLoading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://accounts.google.com/gsi/client';
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => {
      gisLoading = null;
      reject(new Error('No se pudo cargar Google'));
    };
    document.head.appendChild(s);
  });
  return gisLoading;
}

/**
 * Botón oficial de Google Identity Services. Entrega el `id_token` (credential) que el backend valida en
 * `/auth/google`. Si no hay Client ID configurado no se muestra.
 */
@Component({
  selector: 'app-google-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (enabled) {
      <div class="divider"><span></span>o continúa con<span></span></div>
      <div #host class="gis"></div>
      @if (failed()) {
        <p class="hint">No se pudo cargar el acceso con Google.</p>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .divider {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: var(--text-2);
    }
    .divider span {
      flex: 1;
      height: 1px;
      background: var(--border);
    }
    .gis {
      display: flex;
      justify-content: center;
      min-height: 44px;
    }
  `,
})
export class GoogleButton {
  private readonly config = inject(AppConfig);
  readonly text = input<'signin_with' | 'signup_with' | 'continue_with'>('continue_with');
  readonly credential = output<string>();
  protected readonly failed = signal(false);
  protected readonly enabled = !!this.config.googleClientId;
  private readonly host = viewChild<ElementRef<HTMLElement>>('host');

  constructor() {
    afterNextRender(async () => {
      const el = this.host()?.nativeElement;
      if (!el) return;
      try {
        await loadGis();
        const id = window.google!.accounts.id;
        id.initialize({
          client_id: this.config.googleClientId,
          callback: (r: { credential: string }) => this.credential.emit(r.credential),
          ux_mode: 'popup',
        });
        id.renderButton(el, {
          theme: 'outline',
          size: 'large',
          shape: 'rectangular',
          text: this.text(),
          locale: 'es',
          width: Math.min(el.clientWidth || 360, 400),
        });
      } catch {
        this.failed.set(true);
      }
    });
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NotificationPreferences, NotificationType } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { errorMessage } from '../../core/problem';
import { Icon } from '../../shared/icon';

const TYPES: [NotificationType, string][] = [
  ['EVENT', 'Eventos de la agenda'],
  ['PAYMENT', 'Pagos por vencer'],
  ['CELEBRATION', 'Cumpleaños y fechas especiales'],
];

/** Sección «Notificaciones» de Configuración: push en este navegador, tipos silenciados y horas de silencio. */
@Component({
  selector: 'app-notification-settings',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>Notificaciones</h2>
      @if (message(); as m) {
        <div class="alert" [class.info]="m.ok" [class.error]="!m.ok" role="status">
          {{ m.text }}
        </div>
      }
      <div class="push">
        <span class="ico"><app-icon name="bell" [size]="20" /></span>
        <span class="grow">
          <b>En este navegador</b>
          <span class="hint">{{ stateText() }}</span>
        </span>
        @switch (service.pushState()) {
          @case ('on') {
            <button class="btn ghost small" type="button" (click)="test()" [disabled]="busy()">
              Probar
            </button>
            <button class="btn ghost small" type="button" (click)="off()" [disabled]="busy()">
              Desactivar
            </button>
          }
          @case ('off') {
            <button class="btn small" type="button" (click)="on()" [disabled]="busy()">
              Activar
            </button>
          }
        }
      </div>

      @if (prefs(); as p) {
        <form class="form" (ngSubmit)="save()">
          <fieldset>
            <legend class="label">Avisarme de</legend>
            @for (t of types; track t[0]) {
              <label class="check">
                <input
                  type="checkbox"
                  [name]="t[0]"
                  [checked]="!p.mutedTypes.includes(t[0])"
                  (change)="toggle(t[0])"
                />
                {{ t[1] }}
              </label>
            }
          </fieldset>
          <div class="field">
            <span class="label">Horas de silencio (sin push; los avisos quedan en la campana)</span>
            <div class="quiet">
              <input
                class="input"
                type="time"
                name="from"
                aria-label="Desde"
                [(ngModel)]="p.quietFrom"
              />
              <span>a</span>
              <input
                class="input"
                type="time"
                name="to"
                aria-label="Hasta"
                [(ngModel)]="p.quietTo"
              />
            </div>
          </div>
          <div class="actions">
            <button class="btn" type="submit" [disabled]="busy()">Guardar</button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    .push {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin: 8px 0 16px;
    }
    .ico {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: var(--primary-soft);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .grow {
      flex: 1;
      min-width: 160px;
      display: flex;
      flex-direction: column;
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .check {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 14px;
    }
    .check input {
      width: 18px;
      height: 18px;
      accent-color: var(--primary);
    }
    .quiet {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .quiet .input {
      max-width: 140px;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class NotificationSettings {
  protected readonly service = inject(NotificationsService);
  protected readonly types = TYPES;
  protected readonly busy = signal(false);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly prefs = signal<NotificationPreferences | null>(null);

  constructor() {
    this.service
      .preferences()
      .then((p) => this.prefs.set({ ...p, quietFrom: p.quietFrom ?? '', quietTo: p.quietTo ?? '' }))
      .catch((e) => this.message.set({ ok: false, text: errorMessage(e) }));
    this.service.refreshPushState().catch(() => undefined);
  }

  protected stateText(): string {
    switch (this.service.pushState()) {
      case 'on':
        return 'Recibes avisos aunque Famy esté cerrada.';
      case 'denied':
        return 'El navegador bloqueó las notificaciones. Permítelas en la configuración del sitio.';
      case 'unsupported':
        return 'Este navegador no admite notificaciones push (en iPhone, primero añade Famy a la pantalla de inicio).';
      default:
        return 'Activa los avisos para recibir recordatorios con la app cerrada.';
    }
  }

  protected toggle(t: NotificationType) {
    const p = this.prefs();
    if (!p) return;
    const muted = p.mutedTypes.includes(t)
      ? p.mutedTypes.filter((x) => x !== t)
      : [...p.mutedTypes, t];
    this.prefs.set({ ...p, mutedTypes: muted });
  }

  protected on() {
    return this.run('Notificaciones activadas en este navegador.', () => this.service.enablePush());
  }

  protected off() {
    return this.run('Notificaciones desactivadas en este navegador.', () =>
      this.service.disablePush(),
    );
  }

  protected test() {
    return this.run('Te enviamos un aviso de prueba.', () => this.service.sendTest());
  }

  protected save() {
    const p = this.prefs()!;
    return this.run('Preferencias guardadas.', async () => {
      const saved = await this.service.savePreferences({
        mutedTypes: p.mutedTypes,
        quietFrom: p.quietFrom || null,
        quietTo: p.quietTo || null,
      });
      this.prefs.set({ ...saved, quietFrom: saved.quietFrom ?? '', quietTo: saved.quietTo ?? '' });
    });
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

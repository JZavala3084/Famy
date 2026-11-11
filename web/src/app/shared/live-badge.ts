import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RealtimeService } from '../core/realtime.service';

/** Indicador «En vivo» del diseño: verde con WebSocket conectado, gris mientras reconecta. */
@Component({
  selector: 'app-live-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="dot"></span>{{ label() }}`,
  host: {
    '[class.live]': "status() === 'live'",
    role: 'status',
    '[attr.title]': 'title()',
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 999px;
      color: var(--text-2);
      background: var(--muted);
      white-space: nowrap;
    }
    .dot {
      width: 7px;
      height: 7px;
      border-radius: 4px;
      background: #9a9ea6;
    }
    :host(.live) {
      color: #146b47;
      background: #ddf3e8;
    }
    :host(.live) .dot {
      background: #1e9e6b;
    }
  `,
})
export class LiveBadge {
  protected readonly status = inject(RealtimeService).status;
  protected readonly label = computed(() =>
    this.status() === 'live'
      ? 'En vivo'
      : this.status() === 'connecting'
        ? 'Conectando…'
        : 'Sin conexión',
  );
  protected readonly title = computed(() =>
    this.status() === 'live'
      ? 'Los cambios de tu familia aparecen al instante'
      : 'Reconectando; los cambios se sincronizan al volver',
  );
}

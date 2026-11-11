import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon';
import { NAV } from '../shell/shell';

const PHASES: Record<string, string> = {
  presupuesto:
    'Fase 6: presupuesto familiar por categorías, movimientos y alertas al exceder un límite.',
  recetas: 'Fase 6: recetas de la familia y «Añadir ingredientes faltantes a Compras».',
  horarios: 'Fase 5: agenda semanal por miembro con eventos que se repiten.',
  pagos: 'Fase 5: calendario de pagos con recordatorios y responsables.',
  cumpleanos: 'Fase 5: cumpleaños con recordatorios 7 y 1 día antes e ideas de regalo.',
  salud: 'Fase 6b: condiciones, medicamentos con horarios y recordatorios de tomas.',
};

@Component({
  selector: 'app-soon',
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <section class="card hero">
        @if (item(); as n) {
          <span class="badge"><app-icon [name]="n.icon" [size]="28" /></span>
          <h1>{{ n.label }}</h1>
        }
        <p class="muted">{{ text() }}</p>
        <a class="btn" routerLink="/">Volver al inicio</a>
      </section>
    </div>
  `,
  styles: `
    .hero {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 14px;
      padding: 48px 24px;
    }
    .badge {
      width: 64px;
      height: 64px;
      border-radius: 20px;
      background: var(--primary-soft);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
    }
  `,
})
export class Soon {
  readonly module = input<string>();
  protected readonly item = computed(() => NAV.find((n) => n.link === `/pronto/${this.module()}`));
  protected readonly text = computed(
    () => PHASES[this.module() ?? ''] ?? 'Este módulo llega en una próxima versión de Famy.',
  );
}

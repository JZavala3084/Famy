import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Icon } from '../../shared/icon';

export interface PasswordRule {
  text: string;
  ok: boolean;
  required: boolean;
}

/** Reglas de 03 · Seguridad: 8+ caracteres, mayúsculas y minúsculas, un número (12+ recomendado). */
export function passwordRules(pw: string): PasswordRule[] {
  return [
    { text: 'Mínimo 8 caracteres', ok: pw.length >= 8, required: true },
    {
      text: 'Una mayúscula y una minúscula',
      ok: /[a-z]/.test(pw) && /[A-Z]/.test(pw),
      required: true,
    },
    { text: 'Al menos un número', ok: /\d/.test(pw), required: true },
    { text: 'Recomendado: 12 caracteres o más', ok: pw.length >= 12, required: false },
  ];
}

export function passwordOk(pw: string): boolean {
  return passwordRules(pw).every((r) => r.ok || !r.required);
}

const LEVELS = ['Muy débil', 'Débil', 'Regular', 'Buena', 'Excelente'];

@Component({
  selector: 'app-password-strength',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bars" aria-hidden="true">
      @for (i of [0, 1, 2, 3]; track i) {
        <span [class.on]="i < score()" [style.background]="i < score() ? color() : null"></span>
      }
    </div>
    <p class="level" [style.color]="color()">Seguridad: {{ level() }}</p>
    <ul>
      @for (r of rules(); track r.text) {
        <li [class.ok]="r.ok">
          <app-icon [name]="r.ok ? 'check' : 'alert'" [size]="16" />
          {{ r.text }}
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .bars {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 6px;
    }
    .bars span {
      height: 5px;
      border-radius: 3px;
      background: var(--muted);
    }
    .level {
      font-size: 13px;
      font-weight: 600;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 13px;
      color: var(--text-2);
    }
    li {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    li.ok {
      color: var(--primary);
    }
  `,
})
export class PasswordStrength {
  readonly password = input('');
  protected readonly rules = computed(() => passwordRules(this.password()));
  protected readonly score = computed(() => {
    const pw = this.password();
    if (!pw) return 0;
    return this.rules().filter((r) => r.ok).length;
  });
  protected readonly level = computed(() => LEVELS[this.score()]);
  protected readonly color = computed(() =>
    this.score() >= 3 ? 'var(--primary)' : this.score() === 0 ? 'var(--text-2)' : 'var(--accent)',
  );
}

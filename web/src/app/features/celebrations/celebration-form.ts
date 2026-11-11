import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Celebration, CelebrationInput, CelebrationType, GiftIdea } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Icon } from '../../shared/icon';
import { CelebrationsStore } from './celebrations.store';

export const MONTHS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

/**
 * Diálogo de un cumpleaños o fecha especial. Si es el cumpleaños de un miembro, la fecha viene de su perfil y aquí
 * solo se editan las notas y las ideas de regalo (que la persona festejada no ve).
 */
@Component({
  selector: 'app-celebration-form',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="sheet" (close)="dialog.open || open.set(false)">
      @if (open()) {
        <form class="sheet-body" (ngSubmit)="save()">
          <h2>{{ title() }}</h2>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          @if (member()) {
            <p class="hint">
              La fecha sale del perfil de {{ f.personName }}; cada quien la cambia en Configuración.
            </p>
          } @else {
            <div class="field">
              <label for="cb-type">Tipo</label>
              <select id="cb-type" class="input" name="type" [(ngModel)]="f.type">
                <option value="BIRTHDAY">Cumpleaños</option>
                <option value="ANNIVERSARY">Aniversario</option>
                <option value="OTHER">Otra fecha</option>
              </select>
            </div>
            <div class="field">
              <label for="cb-name">{{ f.type === 'BIRTHDAY' ? 'Persona' : 'Nombre' }}</label>
              <input
                id="cb-name"
                class="input"
                name="name"
                maxlength="120"
                [placeholder]="f.type === 'BIRTHDAY' ? 'Abuela Rosa' : 'Boda de mamá y papá'"
                required
                [(ngModel)]="f.personName"
              />
            </div>
            <div class="grid3">
              <div class="field">
                <label for="cb-day">Día</label>
                <input
                  id="cb-day"
                  class="input"
                  name="day"
                  type="number"
                  min="1"
                  max="31"
                  required
                  [(ngModel)]="f.day"
                />
              </div>
              <div class="field">
                <label for="cb-month">Mes</label>
                <select id="cb-month" class="input" name="month" [(ngModel)]="f.month">
                  @for (m of months; track $index) {
                    <option [ngValue]="$index + 1">{{ m }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label for="cb-year">Año (opcional)</label>
                <input
                  id="cb-year"
                  class="input"
                  name="year"
                  type="number"
                  min="1900"
                  [(ngModel)]="f.year"
                />
              </div>
            </div>
          }
          <div class="field">
            <label for="cb-notes">Notas (opcional)</label>
            <textarea
              id="cb-notes"
              class="input"
              name="notes"
              rows="2"
              maxlength="1000"
              [(ngModel)]="f.notes"
            ></textarea>
          </div>

          @if (!celebration()?.giftIdeasHidden) {
            <div class="field">
              <span class="label">Ideas de regalo</span>
              @if (member()) {
                <span class="hint">{{ f.personName }} no las ve.</span>
              }
              <ul class="gifts">
                @for (g of f.gifts; track $index) {
                  <li>
                    <input
                      type="checkbox"
                      [name]="'g' + $index"
                      [(ngModel)]="g.done"
                      [attr.aria-label]="'Comprado: ' + g.text"
                    />
                    <span [class.done]="g.done">{{ g.text }}</span>
                    <button
                      class="icon-btn"
                      type="button"
                      aria-label="Quitar idea"
                      (click)="removeGift($index)"
                    >
                      <app-icon name="x" [size]="16" />
                    </button>
                  </li>
                }
              </ul>
              <div class="add">
                <input
                  class="input"
                  name="newGift"
                  maxlength="200"
                  placeholder="Agregar una idea…"
                  [(ngModel)]="newGift"
                  (keydown.enter)="$event.preventDefault(); addGift()"
                />
                <button
                  class="btn ghost"
                  type="button"
                  (click)="addGift()"
                  [disabled]="!newGift.trim()"
                >
                  Agregar
                </button>
              </div>
            </div>
          }

          <div class="sheet-actions">
            @if (celebration()?.id && !member()) {
              <button class="btn danger" type="button" (click)="remove()" [disabled]="busy()">
                <app-icon name="trash" [size]="18" /> Borrar
              </button>
            }
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="close()">Cancelar</button>
            <button
              class="btn"
              type="submit"
              [disabled]="busy() || (!member() && (!f.personName.trim() || !f.day))"
            >
              Guardar
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .grid3 {
      display: grid;
      grid-template-columns: 0.8fr 1.4fr 1fr;
      gap: 12px;
    }
    .gifts {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .gifts li {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: 40px;
    }
    .gifts input {
      width: 18px;
      height: 18px;
      accent-color: var(--primary);
    }
    .gifts span {
      flex: 1;
      font-size: 14px;
    }
    .gifts .done {
      text-decoration: line-through;
      color: var(--text-2);
    }
    .add {
      display: flex;
      gap: 8px;
    }
    .grow {
      flex: 1;
    }
  `,
})
export class CelebrationForm {
  private readonly store = inject(CelebrationsStore);

  protected readonly months = MONTHS;
  protected readonly open = signal(false);
  protected readonly celebration = signal<Celebration | null>(null);
  protected readonly member = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected f = this.blank();
  protected newGift = '';
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected title() {
    const c = this.celebration();
    if (!c) return 'Nueva fecha';
    return c.type === 'BIRTHDAY' ? `Cumpleaños de ${c.personName}` : (c.personName ?? 'Fecha');
  }

  show(c: Celebration | null) {
    this.celebration.set(c);
    this.member.set(!!c?.userId);
    this.error.set(null);
    this.newGift = '';
    this.f = c
      ? {
          type: c.type,
          personName: c.personName ?? '',
          month: c.month ?? 1,
          day: c.day as number | null,
          year: c.year as number | null,
          notes: c.notes ?? '',
          gifts: (c.giftIdeas ?? []).map((g) => ({ ...g })),
        }
      : this.blank();
    this.open.set(true);
    this.dialog().nativeElement.showModal();
  }

  close() {
    this.dialog().nativeElement.close();
  }

  protected addGift() {
    const text = this.newGift.trim();
    if (!text) return;
    this.f.gifts = [...this.f.gifts, { text, done: false }];
    this.newGift = '';
  }

  protected removeGift(i: number) {
    this.f.gifts = this.f.gifts.filter((_, j) => j !== i);
  }

  async save() {
    this.addGift();
    const c = this.celebration();
    const f = this.f;
    const hidden = !!c?.giftIdeasHidden;
    const input: CelebrationInput = this.member()
      ? { userId: c!.userId, notes: f.notes.trim() || null }
      : {
          type: f.type,
          personName: f.personName.trim(),
          month: Number(f.month),
          day: Number(f.day),
          year: f.year ? Number(f.year) : null,
          notes: f.notes.trim() || null,
        };
    if (!hidden) input.giftIdeas = f.gifts;
    await this.run(() => this.store.save(c, input));
  }

  async remove() {
    const c = this.celebration();
    if (!c || !confirm(`¿Borrar «${c.personName}»?`)) return;
    await this.run(() => this.store.remove(c));
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.close();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  private blank() {
    return {
      type: 'BIRTHDAY' as CelebrationType,
      personName: '',
      month: new Date().getMonth() + 1,
      day: null as number | null,
      year: null as number | null,
      notes: '',
      gifts: [] as GiftIdea[],
    };
  }
}

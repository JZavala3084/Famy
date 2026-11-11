import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { dayKey } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { Payment, PaymentInput } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Icon } from '../../shared/icon';
import { PaymentsStore } from './payments.store';

/** Frecuencias que ofrece el formulario (cualquier otra RRULE se conserva como «personalizada»). */
export const PAYMENT_REPEATS: [string, string][] = [
  ['', 'Una sola vez'],
  ['FREQ=WEEKLY', 'Cada semana'],
  ['FREQ=WEEKLY;INTERVAL=2', 'Cada dos semanas'],
  ['FREQ=MONTHLY', 'Cada mes'],
  ['FREQ=MONTHLY;INTERVAL=2', 'Cada dos meses'],
  ['FREQ=MONTHLY;INTERVAL=3', 'Cada tres meses'],
  ['FREQ=YEARLY', 'Cada año'],
];

export function describePaymentRepeat(rrule: string | null): string {
  const found = PAYMENT_REPEATS.find(([r]) => r === (rrule ?? ''));
  return found ? found[1] : 'Personalizada';
}

/** Diálogo para crear o editar un pago (administradores de la familia). */
@Component({
  selector: 'app-payment-form',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="sheet" (close)="dialog.open || open.set(false)">
      @if (open()) {
        <form class="sheet-body" (ngSubmit)="save()">
          <h2>{{ payment() ? 'Editar pago' : 'Nuevo pago' }}</h2>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <div class="field">
            <label for="p-name">Pago</label>
            <input
              id="p-name"
              class="input"
              name="name"
              maxlength="120"
              placeholder="Renta, luz, internet…"
              required
              [(ngModel)]="f.name"
            />
          </div>
          <div class="grid2">
            <div class="field">
              <label for="p-amount">Monto</label>
              <input
                id="p-amount"
                class="input"
                name="amount"
                type="number"
                min="0"
                step="0.01"
                inputmode="decimal"
                required
                [(ngModel)]="f.amount"
              />
            </div>
            <div class="field">
              <label for="p-cur">Moneda</label>
              <input
                id="p-cur"
                class="input"
                name="currency"
                maxlength="3"
                [(ngModel)]="f.currency"
              />
            </div>
          </div>
          <div class="grid2">
            <div class="field">
              <label for="p-due">{{
                f.repeat || f.custom ? 'Primer vencimiento' : 'Vence el'
              }}</label>
              <input
                id="p-due"
                class="input"
                name="due"
                type="date"
                required
                [(ngModel)]="f.firstDueOn"
              />
            </div>
            <div class="field">
              <label for="p-rep">Se repite</label>
              <select id="p-rep" class="input" name="repeat" [(ngModel)]="f.repeat">
                @for (r of repeats; track r[0]) {
                  <option [value]="r[0]">{{ r[1] }}</option>
                }
                @if (f.custom) {
                  <option [value]="f.custom">Personalizada</option>
                }
              </select>
            </div>
          </div>
          <div class="grid2">
            <div class="field">
              <label for="p-remind">Avisar días antes</label>
              <input
                id="p-remind"
                class="input"
                name="remind"
                type="number"
                min="0"
                max="30"
                [(ngModel)]="f.remindDaysBefore"
              />
            </div>
            <div class="field">
              <label for="p-who">Responsable</label>
              <select id="p-who" class="input" name="who" [(ngModel)]="f.responsibleId">
                <option value="">Sin asignar</option>
                @for (m of adults(); track m.id) {
                  <option [value]="m.id">{{ m.name }}</option>
                }
              </select>
            </div>
          </div>
          <div class="field">
            <label for="p-cat">Categoría (opcional)</label>
            <input
              id="p-cat"
              class="input"
              name="category"
              maxlength="60"
              placeholder="Casa, servicios, escuela…"
              [(ngModel)]="f.category"
            />
          </div>
          <div class="field">
            <label for="p-notes">Notas (opcional)</label>
            <textarea
              id="p-notes"
              class="input"
              name="notes"
              rows="2"
              maxlength="1000"
              [(ngModel)]="f.notes"
            ></textarea>
          </div>
          <div class="sheet-actions">
            @if (payment(); as p) {
              <button class="btn danger" type="button" (click)="remove(p)" [disabled]="busy()">
                <app-icon name="trash" [size]="18" /> Borrar
              </button>
            }
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="close()">Cancelar</button>
            <button
              class="btn"
              type="submit"
              [disabled]="busy() || !f.name.trim() || !f.firstDueOn || f.amount === null"
            >
              Guardar
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .grid2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .grow {
      flex: 1;
    }
  `,
})
export class PaymentForm {
  private readonly family = inject(FamilyService);
  private readonly store = inject(PaymentsStore);

  protected readonly repeats = PAYMENT_REPEATS;
  protected readonly open = signal(false);
  protected readonly payment = signal<Payment | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly adults = computed(() =>
    this.family.members().filter((m) => m.role !== 'CHILD'),
  );
  protected f = this.blank(null);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  show(payment: Payment | null, day: string | null = null) {
    this.payment.set(payment);
    this.error.set(null);
    if (payment) {
      const known = PAYMENT_REPEATS.some(([r]) => r === (payment.rrule ?? ''));
      this.f = {
        name: payment.name,
        amount: payment.amount as number | null,
        currency: payment.currency,
        firstDueOn: payment.firstDueOn,
        repeat: payment.rrule ?? '',
        custom: known ? null : payment.rrule,
        remindDaysBefore: payment.remindDaysBefore,
        responsibleId: payment.responsibleId ?? '',
        category: payment.category ?? '',
        notes: payment.notes ?? '',
      };
    } else {
      this.f = this.blank(day);
    }
    this.open.set(true);
    this.dialog().nativeElement.showModal();
  }

  close() {
    this.dialog().nativeElement.close();
  }

  async save() {
    const f = this.f;
    const input: PaymentInput = {
      name: f.name.trim(),
      amount: Number(f.amount) || 0,
      currency: f.currency.trim().toUpperCase() || null,
      rrule: f.repeat || null,
      firstDueOn: f.firstDueOn,
      remindDaysBefore: Number(f.remindDaysBefore) || 0,
      responsibleId: f.responsibleId || null,
      category: f.category.trim() || null,
      notes: f.notes.trim() || null,
    };
    const current = this.payment();
    await this.run(() => (current ? this.store.update(current, input) : this.store.create(input)));
  }

  async remove(p: Payment) {
    if (!confirm(`¿Borrar «${p.name}» y su historial de pagos?`)) return;
    await this.run(() => this.store.remove(p));
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

  private blank(day: string | null) {
    return {
      name: '',
      amount: null as number | null,
      currency: this.family.current()?.currency ?? 'USD',
      firstDueOn: day ?? dayKey(new Date(), this.family.timezone()),
      repeat: 'FREQ=MONTHLY',
      custom: null as string | null,
      remindDaysBefore: 3,
      responsibleId: '',
      category: '',
      notes: '',
    };
  }
}

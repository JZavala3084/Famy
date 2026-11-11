import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  addDays,
  addMonths,
  dayKey,
  daysBetween,
  formatDate,
  monthStart,
  monthTitle,
  money,
  weekStart,
} from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { PaymentOccurrence } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { PaymentForm, describePaymentRepeat } from './payment-form';
import { PaymentsStore } from './payments.store';

const LETTERS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Suma por moneda («$1,200.00 + €50.00»). */
export function totals(list: PaymentOccurrence[], fallback: string): string {
  const sums = new Map<string, number>();
  for (const o of list)
    sums.set(o.currency, (sums.get(o.currency) ?? 0) + (o.amountPaid ?? o.amount));
  if (!sums.size) return money(0, fallback);
  return [...sums].map(([c, v]) => money(v, c)).join(' + ');
}

/** Calendario de pagos (RF-7): lo que vence en el mes, vencidos, marcar pagado y comprobantes. */
@Component({
  selector: 'app-payments',
  imports: [FormsModule, NgTemplateOutlet, Avatar, Icon, LiveBadge, PaymentForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './payments.html',
  styleUrl: './payments.css',
})
export class Payments {
  protected readonly family = inject(FamilyService);
  protected readonly store = inject(PaymentsStore);
  protected readonly form = viewChild.required(PaymentForm);
  private readonly payDialog = viewChild.required<ElementRef<HTMLDialogElement>>('pay');

  /** `?mes=YYYY-MM` (los recordatorios y la agenda enlazan al mes del vencimiento). */
  readonly mes = input<string | undefined>();

  protected readonly letters = LETTERS;
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly today = computed(() => dayKey(new Date(), this.family.timezone()));
  protected readonly month = signal(monthStart(dayKey(new Date(), this.family.timezone())));
  protected readonly selectedDay = signal<string | null>(null);
  private readonly currency = computed(() => this.family.current()?.currency ?? 'USD');

  private readonly monthRange = computed(() => {
    const from = this.month();
    return { from, to: addDays(addMonths(from, 1), -1) };
  });
  /** Ventana de vencidos: lo no pagado de los últimos seis meses. */
  private readonly overdueRange = computed(() => ({
    from: addDays(this.today(), -183),
    to: addDays(this.today(), -1),
  }));

  protected readonly monthData = computed(() => {
    const { from, to } = this.monthRange();
    return this.store.occurrences(from, to);
  });
  protected readonly monthList = computed(() => this.monthData()?.occurrences ?? []);
  protected readonly overdue = computed(() => {
    const { from, to } = this.overdueRange();
    return (this.store.occurrences(from, to)?.occurrences ?? []).filter(
      (o) => o.status === 'OVERDUE',
    );
  });
  protected readonly pending = computed(() =>
    this.monthList().filter(
      (o) => o.status === 'PENDING' && (!this.selectedDay() || o.dueOn === this.selectedDay()),
    ),
  );
  protected readonly paid = computed(() =>
    this.monthList().filter(
      (o) => o.status === 'PAID' && (!this.selectedDay() || o.dueOn === this.selectedDay()),
    ),
  );
  protected readonly toPay = computed(() =>
    totals(
      this.monthList().filter((o) => o.status !== 'PAID'),
      this.currency(),
    ),
  );
  protected readonly paidTotal = computed(() =>
    totals(
      this.monthList().filter((o) => o.status === 'PAID'),
      this.currency(),
    ),
  );
  protected readonly progress = computed(() => {
    const list = this.monthList();
    return list.length
      ? Math.round((100 * list.filter((o) => o.status === 'PAID').length) / list.length)
      : 0;
  });

  protected readonly cells = computed(() => {
    const first = this.month();
    const from = weekStart(first);
    const last = addDays(addMonths(first, 1), -1);
    const to = addDays(weekStart(last), 6);
    const byDay = new Map<string, PaymentOccurrence[]>();
    for (const o of this.monthList()) {
      if (!byDay.has(o.dueOn)) byDay.set(o.dueOn, []);
      byDay.get(o.dueOn)!.push(o);
    }
    const cells = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      const list = byDay.get(d) ?? [];
      cells.push({
        key: d,
        n: Number(d.slice(8)),
        inMonth: d.startsWith(first.slice(0, 7)),
        status: list.some((o) => o.status === 'OVERDUE')
          ? 'overdue'
          : list.some((o) => o.status === 'PENDING')
            ? 'pending'
            : list.length
              ? 'paid'
              : '',
      });
    }
    return cells;
  });

  /** Diálogo «Marcar pagado». */
  protected readonly paying = signal<PaymentOccurrence | null>(null);
  protected p = { amount: 0, paidOn: '', note: '', file: null as File | null };

  constructor() {
    effect(() => {
      const m = this.mes();
      if (m && /^\d{4}-\d{2}$/.test(m)) untracked(() => this.month.set(`${m}-01`));
    });
    effect(() => {
      const a = this.monthRange();
      const b = this.overdueRange();
      untracked(() => {
        const fail = (e: unknown) => this.error.set(errorMessage(e));
        this.store.load(a.from, a.to).catch(fail);
        this.store.load(b.from, b.to).catch(fail);
      });
    });
  }

  protected title = computed(() => monthTitle(this.month()));

  protected move(step: number) {
    this.selectedDay.set(null);
    this.month.set(addMonths(this.month(), step));
  }

  protected toggleDay(key: string) {
    this.selectedDay.set(this.selectedDay() === key ? null : key);
  }

  protected amount(o: PaymentOccurrence) {
    return money(o.amountPaid ?? o.amount, o.currency);
  }

  protected due(o: PaymentOccurrence): string {
    const diff = daysBetween(this.today(), o.dueOn);
    if (o.status === 'PAID') {
      return o.paidOn
        ? `Pagado el ${formatDate(o.paidOn, { day: 'numeric', month: 'short' })}`
        : 'Pagado';
    }
    if (diff === 0) return 'Vence hoy';
    if (diff === 1) return 'Vence mañana';
    if (diff < 0) return `Venció hace ${-diff} ${diff === -1 ? 'día' : 'días'}`;
    return `Vence el ${formatDate(o.dueOn, { day: 'numeric', month: 'short' })} · en ${diff} días`;
  }

  protected repeat(o: PaymentOccurrence): string {
    const p = this.store.payments()?.find((x) => x.id === o.paymentId);
    return p?.rrule ? describePaymentRepeat(p.rrule) : 'Una sola vez';
  }

  protected edit(o: PaymentOccurrence) {
    if (!this.family.isManager()) return;
    const p = this.store.payments()?.find((x) => x.id === o.paymentId);
    if (p) this.form().show(p);
  }

  protected openPay(o: PaymentOccurrence) {
    this.p = { amount: o.amount, paidOn: this.today(), note: '', file: null };
    this.paying.set(o);
    this.payDialog().nativeElement.showModal();
  }

  protected closePay() {
    this.payDialog().nativeElement.close();
  }

  protected pickFile(event: Event) {
    this.p.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected async confirmPay() {
    const o = this.paying();
    if (!o) return;
    await this.run(async () => {
      const res = await this.store.pay(o, {
        amountPaid: Number(this.p.amount),
        paidOn: this.p.paidOn || null,
        note: this.p.note.trim() || null,
      });
      if (this.p.file) await this.store.attachReceipt(res, this.p.file);
      this.closePay();
    });
  }

  protected async unpay(o: PaymentOccurrence) {
    if (!confirm(`¿Marcar «${o.name}» como no pagado?`)) return;
    await this.run(() => this.store.unpay(o));
  }

  protected async attach(o: PaymentOccurrence, event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) await this.run(() => this.store.attachReceipt(o, file).then(() => undefined));
  }

  protected async receipt(o: PaymentOccurrence) {
    await this.run(() => this.store.openReceipt(o));
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

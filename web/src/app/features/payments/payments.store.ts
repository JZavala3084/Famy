import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from '../../core/config';
import { FamilyService } from '../../core/family.service';
import { OccurrenceRange, Payment, PaymentInput, PaymentOccurrence } from '../../core/models';
import { RangeCache } from '../../core/range-cache';
import { RealtimeService } from '../../core/realtime.service';
import { upsert } from '../../core/versioned';

/** Pagos y sus vencimientos (solo adultos), sincronizados con `payment` y `payment.occurrence`. */
@Injectable({ providedIn: 'root' })
export class PaymentsStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);
  private familyId: string | null = null;

  readonly payments = signal<Payment[] | null>(null);
  private readonly cache = new RangeCache<OccurrenceRange>((from, to) =>
    firstValueFrom(
      this.http.get<OccurrenceRange>(this.url('/occurrences'), { params: { from, to } }),
    ),
  );

  constructor() {
    this.realtime.events.subscribe((e) => {
      if (!e.entity.startsWith('payment') || e.familyId !== this.familyId) return;
      if (e.entity === 'payment') {
        if (e.op === 'D') this.payments.update((l) => l && l.filter((p) => p.id !== e.entityId));
        else this.payments.update((l) => l && upsert(l, e.payload as Payment));
      }
      this.cache.reloadSoon();
    });
    this.realtime.resync.subscribe(() => {
      this.loadPayments().catch(() => undefined);
      this.cache.reloadSoon(0);
    });
  }

  occurrences(from: string, to: string): OccurrenceRange | undefined {
    return this.cache.get(from, to);
  }

  /** Solo para adultos: un CHILD recibe 403 y no debe pedirlos. */
  async load(from: string, to: string): Promise<void> {
    if (!this.family.isAdult()) return;
    this.ensureFamily();
    await this.realtime.ready;
    await Promise.all([this.payments() ? null : this.loadPayments(), this.cache.load(from, to)]);
  }

  async loadPayments(): Promise<void> {
    if (!this.familyId) return;
    this.payments.set(await firstValueFrom(this.http.get<Payment[]>(this.url(''))));
  }

  async create(input: PaymentInput): Promise<void> {
    const p = await firstValueFrom(this.http.post<Payment>(this.url(''), input));
    this.payments.update((l) => (l ? upsert(l, p) : [p]));
    this.cache.reloadSoon(0);
  }

  async update(payment: Payment, input: PaymentInput): Promise<void> {
    const p = await firstValueFrom(
      this.http.put<Payment>(this.url(`/${payment.id}`), { ...input, version: payment.version }),
    );
    this.payments.update((l) => (l ? upsert(l, p) : [p]));
    this.cache.reloadSoon(0);
  }

  async remove(payment: Payment): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/${payment.id}`)));
    this.payments.update((l) => l && l.filter((p) => p.id !== payment.id));
    this.cache.reloadSoon(0);
  }

  async pay(
    o: PaymentOccurrence,
    body: { amountPaid?: number | null; paidOn?: string | null; note?: string | null } = {},
  ): Promise<PaymentOccurrence> {
    const res = await firstValueFrom(
      this.http.post<PaymentOccurrence>(
        this.url(`/${o.paymentId}/occurrences/${o.dueOn}/pay`),
        body,
      ),
    );
    this.cache.reloadSoon(0);
    this.loadPayments().catch(() => undefined);
    return res;
  }

  async unpay(o: PaymentOccurrence): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/${o.paymentId}/occurrences/${o.dueOn}/pay`)));
    this.cache.reloadSoon(0);
    this.loadPayments().catch(() => undefined);
  }

  async attachReceipt(o: PaymentOccurrence, file: File): Promise<PaymentOccurrence> {
    const form = new FormData();
    form.append('file', file, file.name);
    const res = await firstValueFrom(
      this.http.post<PaymentOccurrence>(
        this.url(`/${o.paymentId}/occurrences/${o.dueOn}/receipt`),
        form,
      ),
    );
    this.cache.reloadSoon(0);
    return res;
  }

  /** Abre el comprobante en otra pestaña (se descarga con el token, no con un enlace público). */
  async openReceipt(o: PaymentOccurrence): Promise<void> {
    const win = window.open('', '_blank');
    try {
      const blob = await firstValueFrom(
        this.http.get(this.url(`/${o.paymentId}/occurrences/${o.dueOn}/receipt`), {
          responseType: 'blob',
        }),
      );
      const url = URL.createObjectURL(blob);
      if (win) win.location.href = url;
      else window.location.href = url;
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      win?.close();
      throw e;
    }
  }

  private ensureFamily(): void {
    const fid = this.family.current()!.id;
    if (fid !== this.familyId) {
      this.familyId = fid;
      this.payments.set(null);
      this.cache.clear();
    }
  }

  private url(path: string): string {
    return this.config.api(`/families/${this.familyId}/payments${path}`);
  }
}

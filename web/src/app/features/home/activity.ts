import {
  CalendarEvent,
  Celebration,
  ChangeEvent,
  Chore,
  ChoreCompletion,
  Payment,
  PaymentOccurrence,
  ShoppingItem,
  ShoppingList,
} from '../../core/models';

/** Texto de la actividad reciente a partir de un evento del change_log (sin el nombre de quién lo hizo). */
export function describeChange(
  e: ChangeEvent,
  choreTitle: (id: string) => string | null,
): string | null {
  const p = e.payload as Record<string, unknown> | null;
  switch (e.entity) {
    case 'shopping.item': {
      const it = p as unknown as ShoppingItem;
      if (e.op === 'C') return `añadió «${it.name}» a Compras`;
      if (e.op === 'D') return 'quitó un artículo de Compras';
      if (it.checked && it.checkedAt && near(it.checkedAt, e.occurredAt))
        return `compró «${it.name}»`;
      return `actualizó «${it.name}» en Compras`;
    }
    case 'shopping.list': {
      const l = p as unknown as ShoppingList;
      if (e.op === 'C') return `creó la lista «${l.name}»`;
      if (e.op === 'D') return 'borró una lista de compras';
      if (l.archived) return `archivó la lista «${l.name}»`;
      return null; // Los contadores cambian con cada artículo: no es actividad interesante.
    }
    case 'chore': {
      const c = p as unknown as Chore;
      if (e.op === 'C') return `creó la tarea «${c.title}»`;
      if (e.op === 'D') return 'borró una tarea';
      return null; // Las marcas llegan como chore.completion.
    }
    case 'chore.completion': {
      if (e.op !== 'C') return 'deshizo una tarea hecha';
      const done = p as unknown as ChoreCompletion;
      const title = choreTitle(done.choreId);
      return title ? `completó «${title}»` : 'completó una tarea';
    }
    case 'calendar.event': {
      const ev = p as unknown as CalendarEvent;
      if (e.op === 'C') return `agendó «${ev.title}»`;
      if (e.op === 'D') return 'borró un evento de la agenda';
      return `actualizó «${ev.title}» en la agenda`;
    }
    case 'payment': {
      const pay = p as unknown as Payment;
      if (e.op === 'C') return `agregó el pago «${pay.name}»`;
      if (e.op === 'D') return 'borró un pago';
      return null; // Cambia también al pagar (próximo vencimiento): ya lo cuenta payment.occurrence.
    }
    case 'payment.occurrence': {
      const o = p as unknown as PaymentOccurrence | null;
      if (e.op === 'D') return o?.name ? `desmarcó el pago «${o.name}»` : 'desmarcó un pago';
      if (e.op === 'C') return `pagó «${o?.name ?? 'un pago'}»`;
      return o?.receiptFileId ? `adjuntó el comprobante de «${o.name}»` : null;
    }
    case 'celebration': {
      const c = p as unknown as Celebration;
      if (e.op === 'C' && !c.userId) return `agregó la fecha «${c.personName}»`;
      if (e.op === 'D') return 'borró una fecha especial';
      return null;
    }
    case 'family.member': {
      const m = p as { active?: boolean };
      if (e.op === 'C') return 'se unió a la familia';
      return m?.active === false ? 'salió de la familia' : 'cambió de rol en la familia';
    }
    default:
      return null;
  }
}

function near(a: string, b: string): boolean {
  return Math.abs(new Date(a).getTime() - new Date(b).getTime()) < 5_000;
}

/** Quién hizo el cambio: en una marca de tarea, la persona que la hizo. */
export function actorOf(e: ChangeEvent): string | null {
  if (e.entity === 'chore.completion' && e.op === 'C') {
    return (e.payload as ChoreCompletion).completedBy ?? e.actor;
  }
  if (e.entity === 'family.member') return e.entityId;
  return e.actor;
}

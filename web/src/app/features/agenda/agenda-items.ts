import { addDays, dayKey, money, timeKey } from '../../core/dates';
import {
  CalendarEvent,
  Celebration,
  Chore,
  EventRange,
  PaymentOccurrence,
} from '../../core/models';

export type AgendaKind = 'event' | 'payment' | 'celebration' | 'chore';

/** Un renglón de la agenda unificada (RF-6.4): eventos, pagos, cumpleaños y tareas con fecha. */
export interface AgendaItem {
  key: string;
  kind: AgendaKind;
  /** Día `YYYY-MM-DD` (zona de la familia). */
  day: string;
  /** `HH:mm` o nulo si es de todo el día. */
  time: string | null;
  endTime: string | null;
  title: string;
  meta: string;
  color: string;
  /** Personas a las que corresponde; vacío = toda la familia. */
  memberIds: string[];
  event?: CalendarEvent;
  occurrenceStart?: string;
  payment?: PaymentOccurrence;
  celebration?: Celebration;
  chore?: Chore;
}

export const KIND_COLORS: Record<AgendaKind, string> = {
  event: '#2F5DA8',
  payment: '#C2562E',
  celebration: '#6B4FA0',
  chore: '#1E6B5E',
};

export interface AgendaSources {
  from: string;
  to: string;
  timeZone: string;
  events?: EventRange;
  payments?: PaymentOccurrence[];
  celebrations?: Celebration[] | null;
  chores?: readonly Chore[];
  memberName: (id: string) => string;
  memberColor: (id: string) => string;
}

function isLeap(y: number): boolean {
  return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
}

/** Fecha de un mes/día en un año (el 29 de febrero cae el 28 en años no bisiestos). */
export function annualDate(year: number, month: number, day: number): string {
  const d = month === 2 && day === 29 && !isLeap(year) ? 28 : day;
  return `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function celebrationTitle(c: Celebration): string {
  switch (c.type) {
    case 'BIRTHDAY':
      return `Cumpleaños de ${c.personName ?? 'alguien'}`;
    case 'ANNIVERSARY':
      return `Aniversario: ${c.personName ?? ''}`;
    default:
      return c.personName ?? 'Fecha especial';
  }
}

function names(ids: string[], sources: AgendaSources): string {
  if (!ids.length) return 'Todos';
  const list = ids.map(sources.memberName);
  return list.length <= 2 ? list.join(' y ') : `${list.slice(0, 2).join(', ')} +${list.length - 2}`;
}

/** Junta todo lo que pasa en `[from, to]`, ordenado por día y hora (los de todo el día primero). */
export function buildAgenda(s: AgendaSources): AgendaItem[] {
  const out: AgendaItem[] = [];
  const tz = s.timeZone;
  const inRange = (d: string) => d >= s.from && d <= s.to;

  if (s.events) {
    const byId = new Map(s.events.events.map((e) => [e.id, e]));
    for (const o of s.events.occurrences) {
      const e = byId.get(o.eventId);
      if (!e) continue;
      const color =
        e.color ||
        (e.participantIds.length ? s.memberColor(e.participantIds[0]) : KIND_COLORS.event);
      const meta = [names(e.participantIds, s), e.location].filter(Boolean).join(' · ');
      if (e.allDay) {
        // Todo el día: un renglón por cada día que abarca.
        const last = dayKey(new Date(new Date(o.end).getTime() - 1), tz);
        for (let d = dayKey(o.start, tz); d <= last; d = addDays(d, 1)) {
          if (inRange(d)) {
            out.push({
              key: `e:${e.id}:${o.start}:${d}`,
              kind: 'event',
              day: d,
              time: null,
              endTime: null,
              title: e.title,
              meta,
              color,
              memberIds: e.participantIds,
              event: e,
              occurrenceStart: o.start,
            });
          }
        }
      } else {
        const d = dayKey(o.start, tz);
        if (!inRange(d)) continue;
        out.push({
          key: `e:${e.id}:${o.start}`,
          kind: 'event',
          day: d,
          time: timeKey(o.start, tz),
          endTime: o.end !== o.start ? timeKey(o.end, tz) : null,
          title: e.title,
          meta,
          color,
          memberIds: e.participantIds,
          event: e,
          occurrenceStart: o.start,
        });
      }
    }
  }

  for (const p of s.payments ?? []) {
    if (!inRange(p.dueOn)) continue;
    const status =
      p.status === 'PAID' ? 'pagado' : p.status === 'OVERDUE' ? 'vencido' : 'por pagar';
    out.push({
      key: `p:${p.paymentId}:${p.dueOn}`,
      kind: 'payment',
      day: p.dueOn,
      time: null,
      endTime: null,
      title: `Pago: ${p.name}`,
      meta: `${money(p.amount, p.currency)} · ${status}`,
      color: KIND_COLORS.payment,
      memberIds: p.responsibleId ? [p.responsibleId] : [],
      payment: p,
    });
  }

  const fromYear = Number(s.from.slice(0, 4));
  const toYear = Number(s.to.slice(0, 4));
  for (const c of s.celebrations ?? []) {
    if (!c.month || !c.day) continue;
    for (let y = fromYear; y <= toYear; y++) {
      const d = annualDate(y, c.month, c.day);
      if (!inRange(d)) continue;
      const years = c.year && c.year < y ? y - c.year : null;
      const meta =
        c.type === 'BIRTHDAY'
          ? years
            ? `Cumple ${years}`
            : 'Cumpleaños'
          : c.type === 'ANNIVERSARY'
            ? years
              ? `${years} ${years === 1 ? 'año' : 'años'}`
              : 'Aniversario'
            : 'Fecha especial';
      out.push({
        key: `c:${c.id ?? c.userId}:${d}`,
        kind: 'celebration',
        day: d,
        time: null,
        endTime: null,
        title: celebrationTitle(c),
        meta,
        color: KIND_COLORS.celebration,
        memberIds: c.userId ? [c.userId] : [],
        celebration: c,
      });
    }
  }

  for (const c of s.chores ?? []) {
    if (c.done || !c.dueAt) continue;
    const d = dayKey(c.dueAt, tz);
    if (!inRange(d)) continue;
    out.push({
      key: `t:${c.id}`,
      kind: 'chore',
      day: d,
      time: timeKey(c.dueAt, tz),
      endTime: null,
      title: c.title,
      meta: c.assigneeId ? `Tarea · ${s.memberName(c.assigneeId)}` : 'Tarea',
      color: KIND_COLORS.chore,
      memberIds: c.assigneeId ? [c.assigneeId] : [],
      chore: c,
    });
  }

  return out.sort(
    (a, b) =>
      a.day.localeCompare(b.day) ||
      (a.time === null ? -1 : 0) - (b.time === null ? -1 : 0) ||
      (a.time ?? '').localeCompare(b.time ?? '') ||
      a.title.localeCompare(b.title),
  );
}

/** ¿Le corresponde a esa persona? (lo de toda la familia, sí). */
export function forMember(item: AgendaItem, memberId: string | null): boolean {
  return !memberId || item.memberIds.length === 0 || item.memberIds.includes(memberId);
}

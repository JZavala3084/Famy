import { addMonths, daysBetween, monthTitle, timeKey, zonedInstant } from '../../core/dates';
import { CalendarEvent, Celebration, PaymentOccurrence } from '../../core/models';
import { totals } from '../payments/payments';
import { annualDate, buildAgenda, forMember } from './agenda-items';

const TZ = 'America/Mexico_City';

function event(over: Partial<CalendarEvent>): CalendarEvent {
  return {
    id: 'e1',
    title: 'Fútbol',
    description: null,
    location: null,
    startsAt: '2026-11-12T23:00:00Z',
    endsAt: '2026-11-13T00:00:00Z',
    allDay: false,
    rrule: null,
    color: null,
    participantIds: ['diego'],
    reminderMinutes: [],
    excludedStarts: [],
    createdBy: 'ana',
    createdAt: '',
    updatedAt: '',
    version: 0,
    ...over,
  };
}

const base = {
  timeZone: TZ,
  memberName: (id: string) => id,
  memberColor: () => '#123456',
};

describe('fechas de la agenda', () => {
  it('convierte día y hora de la zona de la familia a un instante', () => {
    expect(zonedInstant('2026-11-12', '17:00', TZ)).toBe('2026-11-12T23:00:00.000Z');
    expect(timeKey('2026-11-12T23:00:00Z', TZ)).toBe('17:00');
    // Horario de verano en Nueva York: el offset cambia de -4 a -5.
    expect(zonedInstant('2026-07-01', '09:00', 'America/New_York')).toBe(
      '2026-07-01T13:00:00.000Z',
    );
    expect(zonedInstant('2026-12-01', '09:00', 'America/New_York')).toBe(
      '2026-12-01T14:00:00.000Z',
    );
  });

  it('suma meses y cuenta días', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-01');
    expect(addMonths('2026-12-15', 1)).toBe('2027-01-01');
    expect(daysBetween('2026-11-11', '2026-11-22')).toBe(11);
    expect(monthTitle('2026-11-01')).toBe('Noviembre 2026');
  });

  it('el 29 de febrero cae el 28 en años no bisiestos', () => {
    expect(annualDate(2027, 2, 29)).toBe('2027-02-28');
    expect(annualDate(2028, 2, 29)).toBe('2028-02-29');
  });
});

describe('agenda unificada', () => {
  const occurrence = { eventId: 'e1', start: '2026-11-12T23:00:00Z', end: '2026-11-13T00:00:00Z' };

  it('junta eventos, pagos y cumpleaños ordenados por día', () => {
    const pay: PaymentOccurrence = {
      id: null,
      paymentId: 'p1',
      name: 'Renta',
      amount: 1200,
      currency: 'USD',
      dueOn: '2026-11-12',
      status: 'PENDING',
      responsibleId: 'ana',
      category: null,
      paidOn: null,
      paidBy: null,
      amountPaid: null,
      note: null,
      receiptFileId: null,
    };
    const bday = {
      id: null,
      type: 'BIRTHDAY',
      personName: 'Diego',
      userId: 'diego',
      month: 11,
      day: 14,
      year: 2016,
    } as Celebration;
    const items = buildAgenda({
      ...base,
      from: '2026-11-09',
      to: '2026-11-15',
      events: { from: '', to: '', events: [event({})], occurrences: [occurrence] },
      payments: [pay],
      celebrations: [bday],
    });
    expect(items.map((i) => [i.kind, i.day, i.time])).toEqual([
      ['payment', '2026-11-12', null],
      ['event', '2026-11-12', '17:00'],
      ['celebration', '2026-11-14', null],
    ]);
    expect(items[2].meta).toBe('Cumple 10');
    expect(items[1].endTime).toBe('18:00');
  });

  it('un evento de todo el día de varios días aparece en cada uno', () => {
    const e = event({ allDay: true });
    const items = buildAgenda({
      ...base,
      from: '2026-11-01',
      to: '2026-11-30',
      events: {
        from: '',
        to: '',
        events: [e],
        occurrences: [
          { eventId: 'e1', start: '2026-11-20T06:00:00Z', end: '2026-11-22T06:00:00Z' },
        ],
      },
    });
    expect(items.map((i) => i.day)).toEqual(['2026-11-20', '2026-11-21']);
  });

  it('filtra por persona: lo de toda la familia lo ven todos', () => {
    const [mine] = buildAgenda({
      ...base,
      from: '2026-11-12',
      to: '2026-11-12',
      events: { from: '', to: '', events: [event({})], occurrences: [occurrence] },
    });
    expect(forMember(mine, 'diego')).toBe(true);
    expect(forMember(mine, 'ana')).toBe(false);
    expect(forMember({ ...mine, memberIds: [] }, 'ana')).toBe(true);
    expect(forMember(mine, null)).toBe(true);
  });

  it('suma los pagos por moneda', () => {
    const o = { amount: 10, currency: 'USD', amountPaid: null } as PaymentOccurrence;
    expect(totals([], 'USD')).toContain('0.00');
    expect(totals([o, { ...o, amount: 5.5 }], 'USD')).toContain('15.50');
    expect(totals([o, { ...o, currency: 'EUR' }], 'USD')).toContain('+');
  });
});

import { Chore } from '../../core/models';
import { choresForToday } from './chores.store';
import { buildRrule, describeRepeat, parseRepeat } from './rrule';

describe('rrule', () => {
  it('reconoce las repeticiones simples', () => {
    expect(parseRepeat('FREQ=WEEKLY;BYDAY=TU,FR')).toEqual({
      kind: 'weekly',
      days: ['TU', 'FR'],
      raw: 'FREQ=WEEKLY;BYDAY=TU,FR',
    });
    expect(parseRepeat('FREQ=DAILY').kind).toBe('daily');
    expect(parseRepeat('FREQ=DAILY;INTERVAL=2').kind).toBe('custom');
    expect(parseRepeat(null).kind).toBe('none');
  });

  it('construye RRULE con los días en orden', () => {
    expect(buildRrule({ kind: 'weekly', days: ['FR', 'MO'], raw: null })).toBe(
      'FREQ=WEEKLY;BYDAY=MO,FR',
    );
    expect(buildRrule({ kind: 'none', days: [], raw: null })).toBeNull();
  });

  it('describe en español', () => {
    expect(describeRepeat('FREQ=WEEKLY;BYDAY=TU,FR')).toBe('Mar y vie');
    expect(describeRepeat('FREQ=WEEKLY;BYDAY=TU,TH,SA')).toBe('Mar, jue y sáb');
    expect(describeRepeat('FREQ=DAILY')).toBe('Diario');
  });
});

describe('choresForToday', () => {
  const tz = 'America/Mexico_City';
  const chore = (id: string, p: Partial<Chore>) =>
    ({
      id,
      title: id,
      description: null,
      dueAt: null,
      rrule: null,
      points: 0,
      assigneeId: null,
      rotation: false,
      rotationOrder: null,
      done: false,
      completedAt: null,
      createdBy: 'u',
      createdAt: '',
      updatedAt: '',
      version: 0,
      ...p,
    }) as Chore;

  it('incluye atrasadas, de hoy, sin fecha y las hechas hoy; excluye futuras y hechas otro día', () => {
    const today = '2026-11-11';
    const list = choresForToday(
      [
        chore('late', { dueAt: '2026-11-10T15:00:00Z' }),
        chore('today', { dueAt: '2026-11-11T20:00:00Z' }),
        chore('tomorrow', { dueAt: '2026-11-12T20:00:00Z' }),
        // 23:30 en CDMX el 11 = 05:30Z del 12: sigue siendo hoy para la familia
        chore('tonight', { dueAt: '2026-11-12T05:30:00Z' }),
        chore('nodate', {}),
        chore('doneToday', {
          done: true,
          completedAt: '2026-11-11T18:00:00Z',
          dueAt: '2026-11-11T17:00:00Z',
        }),
        // Se repite: al marcarla avanzó al día 13, pero hoy tiene una marca.
        chore('recurringDoneToday', {
          dueAt: '2026-11-13T17:00:00Z',
          rrule: 'FREQ=WEEKLY',
        }),
        chore('doneYesterday', { done: true, completedAt: '2026-11-10T18:00:00Z' }),
      ],
      today,
      tz,
      new Set(['doneToday', 'recurringDoneToday']),
    );
    expect(list.map((c) => c.id).sort()).toEqual(
      ['doneToday', 'late', 'nodate', 'recurringDoneToday', 'today', 'tonight'].sort(),
    );
  });
});

/** Repeticiones simples (RFC 5545) que ofrece el formulario de tareas. */

export const WEEKDAYS = [
  ['MO', 'Lun'],
  ['TU', 'Mar'],
  ['WE', 'Mié'],
  ['TH', 'Jue'],
  ['FR', 'Vie'],
  ['SA', 'Sáb'],
  ['SU', 'Dom'],
] as const;

export type RepeatKind = 'none' | 'daily' | 'weekly' | 'monthly' | 'custom';

export interface Repeat {
  kind: RepeatKind;
  days: string[];
  raw: string | null;
}

function parts(rrule: string): Record<string, string> {
  return Object.fromEntries(
    rrule
      .replace(/^RRULE:/i, '')
      .split(';')
      .filter(Boolean)
      .map((p) => {
        const [k, v] = p.split('=');
        return [k.toUpperCase(), (v ?? '').toUpperCase()];
      }),
  );
}

export function parseRepeat(rrule: string | null): Repeat {
  if (!rrule) return { kind: 'none', days: [], raw: null };
  const p = parts(rrule);
  const keys = Object.keys(p).filter((k) => k !== 'FREQ' && k !== 'BYDAY' && k !== 'WKST');
  const interval = p['INTERVAL'];
  if (keys.length > (interval === '1' ? 1 : 0)) return { kind: 'custom', days: [], raw: rrule };
  if (p['FREQ'] === 'DAILY' && !p['BYDAY']) return { kind: 'daily', days: [], raw: rrule };
  if (p['FREQ'] === 'MONTHLY' && !p['BYDAY']) return { kind: 'monthly', days: [], raw: rrule };
  if (p['FREQ'] === 'WEEKLY') {
    const days = (p['BYDAY'] ?? '').split(',').filter((d) => WEEKDAYS.some(([c]) => c === d));
    return { kind: 'weekly', days, raw: rrule };
  }
  return { kind: 'custom', days: [], raw: rrule };
}

export function buildRrule(r: Repeat): string | null {
  switch (r.kind) {
    case 'none':
      return null;
    case 'daily':
      return 'FREQ=DAILY';
    case 'monthly':
      return 'FREQ=MONTHLY';
    case 'weekly': {
      const days = WEEKDAYS.map(([c]) => c).filter((c) => r.days.includes(c));
      return days.length ? `FREQ=WEEKLY;BYDAY=${days.join(',')}` : 'FREQ=WEEKLY';
    }
    case 'custom':
      return r.raw;
  }
}

/** «Diario», «Mar y vie», «Semanal», «Mensual», «Se repite». */
export function describeRepeat(rrule: string | null): string {
  const r = parseRepeat(rrule);
  switch (r.kind) {
    case 'none':
      return '';
    case 'daily':
      return 'Diario';
    case 'monthly':
      return 'Mensual';
    case 'weekly': {
      if (!r.days.length) return 'Semanal';
      if (r.days.length === 7) return 'Diario';
      const names = WEEKDAYS.filter(([c]) => r.days.includes(c)).map(([, n]) => n.toLowerCase());
      const text =
        names.length === 1
          ? names[0]
          : `${names.slice(0, -1).join(', ')} y ${names[names.length - 1]}`;
      return text.charAt(0).toUpperCase() + text.slice(1);
    }
    case 'custom':
      return 'Se repite';
  }
}

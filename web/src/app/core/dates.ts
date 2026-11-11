/** Utilidades de fechas en la zona horaria de la familia. */

const keyFormatters = new Map<string, Intl.DateTimeFormat>();

/** Fecha local `YYYY-MM-DD` de un instante en la zona dada. */
export function dayKey(instant: Date | string | number, timeZone: string): string {
  let f = keyFormatters.get(timeZone);
  if (!f) {
    f = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    keyFormatters.set(timeZone, f);
  }
  return f.format(new Date(instant));
}

/** Suma días a una fecha `YYYY-MM-DD`. */
export function addDays(key: string, days: number): string {
  const d = new Date(`${key}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Lunes de la semana de una fecha `YYYY-MM-DD` (las semanas de puntaje van de lunes a domingo). */
export function weekStart(key: string): string {
  const dow = new Date(`${key}T12:00:00Z`).getUTCDay(); // 0 = domingo
  return addDays(key, -((dow + 6) % 7));
}

export function formatTime(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat('es', { timeZone, hour: '2-digit', minute: '2-digit' }).format(
    new Date(instant),
  );
}

export function formatDay(instant: string | Date, timeZone: string): string {
  return new Intl.DateTimeFormat('es', {
    timeZone,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date(instant));
}

/** «Martes 10 de noviembre». */
export function longToday(timeZone: string, now = new Date()): string {
  const s = new Intl.DateTimeFormat('es', {
    timeZone,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
    .format(now)
    .replace(',', '');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** «hace 5 min», «ayer»… */
export function relativeTime(instant: string, now = Date.now()): string {
  const diff = Math.round((new Date(instant).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
  const abs = Math.abs(diff);
  if (abs < 45) return 'justo ahora';
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
  return rtf.format(Math.round(diff / 86400), 'day');
}

/** Valor para `<input type="datetime-local">` a partir de un instante (hora local del navegador). */
export function toLocalInput(instant: string | null): string {
  if (!instant) return '';
  const d = new Date(instant);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string): string | null {
  return value ? new Date(value).toISOString() : null;
}

const partFormatters = new Map<string, Intl.DateTimeFormat>();

function zoneParts(instant: Date, timeZone: string): Record<string, number> {
  let f = partFormatters.get(timeZone);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    partFormatters.set(timeZone, f);
  }
  const out: Record<string, number> = {};
  for (const p of f.formatToParts(instant)) {
    if (p.type !== 'literal') out[p.type] = Number(p.value);
  }
  return out;
}

/** Diferencia (ms) entre la hora local de `timeZone` y UTC en ese instante. */
function offsetMs(instant: Date, timeZone: string): number {
  const p = zoneParts(instant, timeZone);
  const asUtc = Date.UTC(p['year'], p['month'] - 1, p['day'], p['hour'], p['minute'], p['second']);
  return asUtc - Math.floor(instant.getTime() / 1000) * 1000;
}

/** Instante ISO de una fecha `YYYY-MM-DD` y hora `HH:mm` en la zona dada (la de la familia). */
export function zonedInstant(day: string, time: string, timeZone: string): string {
  const [y, m, d] = day.split('-').map(Number);
  const [hh, mm] = (time || '00:00').split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  let t = guess - offsetMs(new Date(guess), timeZone);
  // Segunda pasada por si el offset cambió (horario de verano).
  t = guess - offsetMs(new Date(t), timeZone);
  return new Date(t).toISOString();
}

/** Hora `HH:mm` de un instante en la zona dada. */
export function timeKey(instant: string, timeZone: string): string {
  const p = zoneParts(new Date(instant), timeZone);
  return `${String(p['hour']).padStart(2, '0')}:${String(p['minute']).padStart(2, '0')}`;
}

/** Primer día del mes de una fecha `YYYY-MM-DD`. */
export function monthStart(key: string): string {
  return `${key.slice(0, 7)}-01`;
}

/** Suma meses a una fecha `YYYY-MM-DD` (al día 1). */
export function addMonths(key: string, months: number): string {
  const d = new Date(`${monthStart(key)}T12:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() + months);
  return d.toISOString().slice(0, 10);
}

/** Días entre dos fechas `YYYY-MM-DD`. */
export function daysBetween(from: string, to: string): number {
  return Math.round(
    (new Date(`${to}T12:00:00Z`).getTime() - new Date(`${from}T12:00:00Z`).getTime()) / 86_400_000,
  );
}

/** Formatea una fecha `YYYY-MM-DD` con las opciones dadas (en español). */
export function formatDate(key: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('es', { timeZone: 'UTC', ...options }).format(
    new Date(`${key}T12:00:00Z`),
  );
}

/** «Noviembre 2026». */
export function monthTitle(key: string): string {
  const s = formatDate(key, { month: 'long', year: 'numeric' }).replace(' de ', ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** «Domingo 22 de noviembre». */
export function longDate(key: string): string {
  const s = formatDate(key, { weekday: 'long', day: 'numeric', month: 'long' }).replace(',', '');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Monto con su moneda («$1,250.00», «MX$850.00»). */
export function money(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat('es-MX', { style: 'currency', currency }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

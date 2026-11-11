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

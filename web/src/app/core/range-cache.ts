import { signal } from '@angular/core';

/**
 * Caché de respuestas por rango de fechas (`from|to`). Las pantallas piden su rango y leen el resultado como señal;
 * cuando llega un cambio en vivo se recargan todos los rangos que se están usando.
 */
export class RangeCache<T> {
  private readonly data = signal<ReadonlyMap<string, T>>(new Map());
  private readonly inflight = new Map<string, Promise<void>>();
  private order: string[] = [];
  private reloadTimer: ReturnType<typeof setTimeout> | undefined;
  private generation = 0;

  constructor(
    private readonly fetcher: (from: string, to: string) => Promise<T>,
    private readonly maxRanges = 6,
  ) {}

  /** Resultado del rango (o `undefined` si todavía no se carga). Se puede leer dentro de un `computed`. */
  get(from: string, to: string): T | undefined {
    return this.data().get(`${from}|${to}`);
  }

  load(from: string, to: string, force = false): Promise<void> {
    const key = `${from}|${to}`;
    if (!force && this.data().has(key)) return Promise.resolve();
    const pending = this.inflight.get(key);
    if (pending && !force) return pending;
    const gen = this.generation;
    const p = this.fetcher(from, to)
      .then((value) => {
        if (gen !== this.generation) return;
        this.order = [key, ...this.order.filter((k) => k !== key)];
        const next = new Map(this.data());
        next.set(key, value);
        for (const old of this.order.slice(this.maxRanges)) next.delete(old);
        this.order = this.order.slice(0, this.maxRanges);
        this.data.set(next);
      })
      .finally(() => this.inflight.delete(key));
    this.inflight.set(key, p);
    return p;
  }

  /** Recarga (agrupando ráfagas de cambios) todos los rangos en uso. */
  reloadSoon(delay = 300): void {
    clearTimeout(this.reloadTimer);
    this.reloadTimer = setTimeout(() => {
      for (const key of this.order) {
        const [from, to] = key.split('|');
        this.load(from, to, true).catch(() => undefined);
      }
    }, delay);
  }

  clear(): void {
    this.generation++;
    this.order = [];
    this.inflight.clear();
    this.data.set(new Map());
  }
}

/** Utilidades para aplicar cambios con bloqueo optimista (`version`) a colecciones en memoria. */

export interface Versioned {
  id: string;
  version: number;
}

/** Inserta o reemplaza, ignorando datos con versión menor a la que ya se tiene. */
export function upsert<T extends Versioned>(list: readonly T[], next: T): T[] {
  const i = list.findIndex((x) => x.id === next.id);
  if (i < 0) return [...list, next];
  if (list[i].version > next.version) return list as T[];
  const copy = [...list];
  copy[i] = next;
  return copy;
}

export function removeById<T extends { id: string }>(list: readonly T[], id: string): T[] {
  return list.some((x) => x.id === id) ? list.filter((x) => x.id !== id) : (list as T[]);
}

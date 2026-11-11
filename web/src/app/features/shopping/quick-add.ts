import { ItemInput, ShoppingItem } from '../../core/models';

const UNITS: Record<string, string> = {
  kg: 'kg',
  kgs: 'kg',
  g: 'g',
  gr: 'g',
  l: 'L',
  lt: 'L',
  lts: 'L',
  ml: 'ml',
  pza: 'pzas',
  pzas: 'pzas',
  pz: 'pzas',
  pieza: 'pzas',
  piezas: 'pzas',
  docena: 'docena',
  docenas: 'docena',
  paquete: 'paq',
  paquetes: 'paq',
  paq: 'paq',
  caja: 'caja',
  cajas: 'caja',
  lata: 'lata',
  latas: 'lata',
  bolsa: 'bolsa',
  bolsas: 'bolsa',
};

/** «2 kg tomate» → { name: 'Tomate', quantity: 2, unit: 'kg' }. */
export function parseQuickAdd(raw: string): ItemInput | null {
  const text = raw.trim().replace(/\s+/g, ' ');
  if (!text) return null;
  const m = /^(\d+(?:[.,]\d+)?)\s+(?:(\S+)\s+(?:de\s+)?)?(.+)$/i.exec(text);
  if (m) {
    const quantity = Number(m[1].replace(',', '.'));
    const unitWord = m[2]?.toLowerCase();
    const unit = unitWord ? UNITS[unitWord] : undefined;
    const name = unit ? m[3] : [m[2], m[3]].filter(Boolean).join(' ');
    if (quantity > 0 && name) {
      return { name: capitalize(name), quantity, unit: unit ?? null };
    }
  }
  return { name: capitalize(text) };
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** «Lácteos · 2 L». */
export function itemMeta(i: Pick<ShoppingItem, 'category' | 'quantity' | 'unit' | 'note'>): string {
  const qty =
    i.quantity != null
      ? `${Number(i.quantity).toLocaleString('es', { maximumFractionDigits: 3 })}${i.unit ? ' ' + i.unit : ''}`
      : '';
  return [i.category, qty, i.note].filter(Boolean).join(' · ');
}

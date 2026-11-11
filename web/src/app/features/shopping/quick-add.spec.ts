import { itemMeta, parseQuickAdd } from './quick-add';
import { sortItems } from './shopping.store';
import { ShoppingItem } from '../../core/models';

describe('parseQuickAdd', () => {
  it('separa cantidad y unidad', () => {
    expect(parseQuickAdd('2 kg tomate')).toEqual({ name: 'Tomate', quantity: 2, unit: 'kg' });
    expect(parseQuickAdd('1,5 L de leche')).toEqual({ name: 'Leche', quantity: 1.5, unit: 'L' });
    expect(parseQuickAdd('2 docenas de huevos')).toEqual({
      name: 'Huevos',
      quantity: 2,
      unit: 'docena',
    });
  });

  it('una cantidad sin unidad conocida queda en el nombre', () => {
    expect(parseQuickAdd('3 manzanas rojas')).toEqual({
      name: 'Manzanas rojas',
      quantity: 3,
      unit: null,
    });
    expect(parseQuickAdd('6 plátanos')).toEqual({ name: 'Plátanos', quantity: 6, unit: null });
  });

  it('texto simple', () => {
    expect(parseQuickAdd('  leche  deslactosada ')).toEqual({ name: 'Leche deslactosada' });
    expect(parseQuickAdd('   ')).toBeNull();
  });

  it('describe el artículo', () => {
    expect(itemMeta({ category: 'Lácteos', quantity: 2, unit: 'L', note: null })).toBe(
      'Lácteos · 2 L',
    );
    expect(itemMeta({ category: null, quantity: null, unit: null, note: null })).toBe('');
  });
});

describe('sortItems', () => {
  const base = {
    listId: 'l',
    quantity: null,
    unit: null,
    category: null,
    note: null,
    checkedBy: null,
    createdBy: 'u',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '',
    version: 0,
  };
  const mk = (id: string, position: number, checked = false, checkedAt: string | null = null) =>
    ({ ...base, id, name: id, position, checked, checkedAt }) as ShoppingItem;

  it('pendientes por posición y comprados al final, el último marcado primero', () => {
    const sorted = sortItems([
      mk('a', 2),
      mk('b', 1, true, '2026-01-01T10:00:00Z'),
      mk('c', 0),
      mk('d', 3, true, '2026-01-01T11:00:00Z'),
    ]);
    expect(sorted.map((x) => x.id)).toEqual(['c', 'a', 'd', 'b']);
  });
});

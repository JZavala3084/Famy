import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from '../../core/config';
import { FamilyService } from '../../core/family.service';
import { ChangeEvent, ItemInput, ShoppingItem, ShoppingList, Suggestion } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { removeById, upsert } from '../../core/versioned';

/** Pendientes primero (por posición), comprados al final (el último marcado arriba). */
export function sortItems(items: readonly ShoppingItem[]): ShoppingItem[] {
  return [...items].sort((a, b) => {
    if (a.checked !== b.checked) return a.checked ? 1 : -1;
    if (a.checked) return (b.checkedAt ?? '').localeCompare(a.checkedAt ?? '');
    return a.position - b.position || a.createdAt.localeCompare(b.createdAt);
  });
}

/**
 * Estado de Compras: listas e ítems por lista, cargados por REST y mantenidos al día con los eventos
 * `shopping.list` / `shopping.item` del WebSocket.
 */
@Injectable({ providedIn: 'root' })
export class ShoppingStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);

  private familyId: string | null = null;
  private loading = new Set<string>();
  private buffered: ChangeEvent[] = [];

  private readonly listsState = signal<ShoppingList[] | null>(null);
  private readonly itemsState = signal<Record<string, ShoppingItem[]>>({});

  readonly lists = computed(() =>
    (this.listsState() ?? [])
      .filter((l) => !l.archived)
      .sort((a, b) => a.position - b.position || a.createdAt.localeCompare(b.createdAt)),
  );
  readonly loaded = computed(() => this.listsState() !== null);

  constructor() {
    this.realtime.events.subscribe((e) => this.onEvent(e));
    this.realtime.resync.subscribe(() => this.reloadAll());
  }

  items(listId: string): ShoppingItem[] {
    return sortItems(this.itemsState()[listId] ?? []);
  }

  hasItems(listId: string): boolean {
    return listId in this.itemsState();
  }

  /** Pendientes de una lista: desde los ítems si ya se cargaron, si no desde el contador del servidor. */
  pendingCount(listId: string): number {
    const items = this.itemsState()[listId];
    if (items) return items.filter((i) => !i.checked).length;
    return this.listsState()?.find((l) => l.id === listId)?.pendingItems ?? 0;
  }

  checkedCount(listId: string): number {
    const items = this.itemsState()[listId];
    if (items) return items.filter((i) => i.checked).length;
    const l = this.listsState()?.find((x) => x.id === listId);
    return l ? l.totalItems - l.pendingItems : 0;
  }

  readonly totalPending = computed(() => {
    this.itemsState();
    return this.lists().reduce((sum, l) => sum + this.pendingCount(l.id), 0);
  });

  async loadLists(force = false): Promise<void> {
    const fid = this.ensureFamily();
    if (!force && this.listsState() !== null) return;
    await this.realtime.ready;
    this.loading.add('lists');
    try {
      const lists = await firstValueFrom(this.http.get<ShoppingList[]>(this.url('/lists')));
      if (fid === this.familyId) this.listsState.set(lists);
    } finally {
      this.loading.delete('lists');
      this.flush();
    }
  }

  async loadItems(listId: string, force = false): Promise<void> {
    const fid = this.ensureFamily();
    if (!force && this.hasItems(listId)) return;
    await this.realtime.ready;
    this.loading.add(listId);
    try {
      const items = await firstValueFrom(
        this.http.get<ShoppingItem[]>(this.url(`/lists/${listId}/items`)),
      );
      if (fid === this.familyId) this.itemsState.update((s) => ({ ...s, [listId]: items }));
    } finally {
      this.loading.delete(listId);
      this.flush();
    }
  }

  async createList(name: string): Promise<ShoppingList> {
    const list = await firstValueFrom(
      this.http.post<ShoppingList>(this.url('/lists'), { name, icon: 'cart' }),
    );
    this.applyList(list);
    this.itemsState.update((s) => ({ ...s, [list.id]: s[list.id] ?? [] }));
    return list;
  }

  async renameList(list: ShoppingList, name: string): Promise<void> {
    this.applyList(
      await firstValueFrom(
        this.http.patch<ShoppingList>(this.url(`/lists/${list.id}`), {
          name,
          version: list.version,
        }),
      ),
    );
  }

  async archiveList(list: ShoppingList): Promise<void> {
    this.applyList(
      await firstValueFrom(
        this.http.patch<ShoppingList>(this.url(`/lists/${list.id}`), {
          archived: true,
          version: list.version,
        }),
      ),
    );
  }

  async deleteList(listId: string): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/lists/${listId}`)));
    this.listsState.update((l) => (l ? removeById(l, listId) : l));
  }

  async addItem(listId: string, input: ItemInput): Promise<void> {
    const item = await firstValueFrom(
      this.http.post<ShoppingItem>(this.url(`/lists/${listId}/items`), input),
    );
    this.applyItem(item);
  }

  async editItem(item: ShoppingItem, input: ItemInput): Promise<void> {
    const updated = await firstValueFrom(
      this.http.put<ShoppingItem>(this.url(`/lists/${item.listId}/items/${item.id}`), {
        ...input,
        listId: item.listId,
        version: item.version,
      }),
    );
    this.applyItem(updated);
  }

  /** Marca o desmarca al instante y confirma con el servidor (si falla, se revierte). */
  async toggle(item: ShoppingItem, by: string): Promise<void> {
    const checked = !item.checked;
    this.patchLocal({
      ...item,
      checked,
      checkedAt: checked ? new Date().toISOString() : null,
      checkedBy: checked ? by : null,
    });
    try {
      const res = await firstValueFrom(
        this.http.post<ShoppingItem>(
          this.url(`/lists/${item.listId}/items/${item.id}/${checked ? 'check' : 'uncheck'}`),
          {},
        ),
      );
      this.applyItem(res);
    } catch (e) {
      this.patchLocal(item);
      throw e;
    }
  }

  async removeItem(item: ShoppingItem): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/lists/${item.listId}/items/${item.id}`)));
    this.dropItem(item.id);
  }

  async clearChecked(listId: string): Promise<number> {
    const res = await firstValueFrom(
      this.http.post<{ removed: number }>(this.url(`/lists/${listId}/items/clear-checked`), {}),
    );
    this.itemsState.update((s) =>
      s[listId] ? { ...s, [listId]: s[listId].filter((i) => !i.checked) } : s,
    );
    return res.removed;
  }

  suggestions(q: string, listId: string): Promise<Suggestion[]> {
    return firstValueFrom(
      this.http.get<Suggestion[]>(this.url('/suggestions'), {
        params: { q, listId, limit: 6 },
      }),
    );
  }

  // --- Tiempo real -------------------------------------------------------------------------------

  private onEvent(e: ChangeEvent): void {
    if (!e.entity.startsWith('shopping.') || e.familyId !== this.familyId) return;
    if (this.loading.size) {
      this.buffered.push(e);
      return;
    }
    this.apply(e);
  }

  private apply(e: ChangeEvent): void {
    if (e.entity === 'shopping.list') {
      if (e.op === 'D') {
        this.listsState.update((l) => (l ? removeById(l, e.entityId) : l));
        this.itemsState.update((s) => {
          const { [e.entityId]: _, ...rest } = s;
          return rest;
        });
      } else {
        this.applyList(e.payload as ShoppingList);
      }
    } else if (e.entity === 'shopping.item') {
      if (e.op === 'D') this.dropItem(e.entityId);
      else this.applyItem(e.payload as ShoppingItem);
    }
  }

  private flush(): void {
    if (this.loading.size) return;
    const pending = this.buffered;
    this.buffered = [];
    pending.forEach((e) => this.apply(e));
  }

  private applyList(list: ShoppingList): void {
    this.listsState.update((l) => (l ? upsert(l, list) : l));
  }

  private applyItem(item: ShoppingItem): void {
    this.itemsState.update((s) => {
      const next: Record<string, ShoppingItem[]> = {};
      for (const [listId, items] of Object.entries(s)) {
        // Si cambió de lista, se quita de la anterior.
        next[listId] = listId === item.listId ? upsert(items, item) : removeById(items, item.id);
      }
      return next;
    });
  }

  /** Cambio local sin validar versión (actualización optimista). */
  private patchLocal(item: ShoppingItem): void {
    this.itemsState.update((s) => {
      const items = s[item.listId];
      return items ? { ...s, [item.listId]: items.map((i) => (i.id === item.id ? item : i)) } : s;
    });
  }

  private dropItem(id: string): void {
    this.itemsState.update((s) => {
      const next: Record<string, ShoppingItem[]> = {};
      for (const [listId, items] of Object.entries(s)) next[listId] = removeById(items, id);
      return next;
    });
  }

  private async reloadAll(): Promise<void> {
    const loadedLists = Object.keys(this.itemsState());
    this.listsState.set(null);
    this.itemsState.set({});
    await this.loadLists().catch(() => undefined);
    await Promise.all(loadedLists.map((id) => this.loadItems(id).catch(() => undefined)));
  }

  /** Reinicia el estado si cambió la familia activa. */
  private ensureFamily(): string {
    const fid = this.family.current()!.id;
    if (fid !== this.familyId) {
      this.familyId = fid;
      this.listsState.set(null);
      this.itemsState.set({});
      this.buffered = [];
    }
    return fid;
  }

  private url(path: string): string {
    return this.config.api(`/families/${this.familyId}/shopping${path}`);
  }
}

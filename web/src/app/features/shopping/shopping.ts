import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FamilyService } from '../../core/family.service';
import { ShoppingItem, ShoppingList, Suggestion } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { LiveBadge } from '../../shared/live-badge';
import { itemMeta, parseQuickAdd } from './quick-add';
import { ShoppingStore } from './shopping.store';

@Component({
  selector: 'app-shopping',
  imports: [FormsModule, Icon, Avatar, LiveBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <h1>Compras</h1>
        <app-live-badge />
      </header>

      @if (error()) {
        <div class="alert error" role="alert">
          <span class="grow">{{ error() }}</span>
          <button class="icon-btn" type="button" aria-label="Cerrar" (click)="error.set(null)">
            <app-icon name="x" [size]="18" />
          </button>
        </div>
      }

      <div class="chips" role="tablist" aria-label="Listas">
        @for (l of store.lists(); track l.id) {
          <button
            type="button"
            role="tab"
            class="chip"
            [class.on]="l.id === current()?.id"
            [attr.aria-selected]="l.id === current()?.id"
            (click)="select(l.id)"
          >
            {{ l.name }} <span class="count">{{ store.pendingCount(l.id) }}</span>
          </button>
        }
        @if (family.isAdult()) {
          <button type="button" class="chip" (click)="openList(null)">+ Lista</button>
        }
      </div>

      @if (!store.loaded()) {
        <div class="card empty">Cargando listas…</div>
      } @else if (!current()) {
        <div class="card empty">
          <p>Aún no hay listas de compras.</p>
          @if (family.isAdult()) {
            <button class="btn" type="button" (click)="openList(null)">
              Crear la primera lista
            </button>
          }
        </div>
      } @else {
        @let list = current()!;
        <form class="add" (ngSubmit)="add()" autocomplete="off">
          <label for="nuevo" class="sr-only">Añadir artículo</label>
          <div class="add-input">
            <input
              #addInput
              id="nuevo"
              class="input"
              name="nuevo"
              placeholder="Añadir artículo… (p. ej. 2 kg tomate)"
              maxlength="120"
              [ngModel]="draft()"
              (ngModelChange)="onDraft($event)"
              (keydown.escape)="suggestions.set([])"
              (blur)="hideSuggestionsSoon()"
            />
            @if (suggestions().length) {
              <ul class="suggest" role="listbox" aria-label="Sugerencias">
                @for (s of suggestions(); track s.name) {
                  <li>
                    <button
                      type="button"
                      (mousedown)="$event.preventDefault()"
                      (click)="addSuggestion(s)"
                    >
                      <span>{{ s.name }}</span>
                      <span class="muted small">{{ s.category }}</span>
                    </button>
                  </li>
                }
              </ul>
            }
          </div>
          <button
            class="icon-btn primary add-btn"
            type="submit"
            aria-label="Añadir"
            [disabled]="!draft().trim()"
          >
            <app-icon name="plus" [size]="22" />
          </button>
        </form>

        <div class="summary">
          <span>
            {{ store.pendingCount(list.id) }} pendientes · {{ store.checkedCount(list.id) }} en el
            carrito
          </span>
          <span class="actions">
            @if (store.checkedCount(list.id) > 0) {
              <button class="link-btn" type="button" (click)="clearChecked(list)">
                Quitar comprados
              </button>
            }
            @if (family.isAdult()) {
              <button
                class="icon-btn"
                type="button"
                aria-label="Opciones de la lista"
                (click)="openList(list)"
              >
                <app-icon name="more" />
              </button>
            }
          </span>
        </div>

        @if (!store.hasItems(list.id)) {
          <div class="card empty">Cargando artículos…</div>
        } @else if (items().length === 0) {
          <div class="card empty">La lista está vacía. Añade lo que haga falta.</div>
        } @else {
          <ul class="items">
            @for (it of items(); track it.id) {
              <li [class.done]="it.checked">
                <button
                  type="button"
                  class="toggle"
                  role="checkbox"
                  [attr.aria-checked]="it.checked"
                  (click)="toggle(it)"
                >
                  <span class="box"><app-icon name="check" [size]="16" [stroke]="3" /></span>
                  <span class="text">
                    <span class="name">{{ it.name }}</span>
                    @if (meta(it); as m) {
                      <span class="meta">{{ m }}</span>
                    }
                  </span>
                </button>
                <app-avatar [member]="family.member(it.createdBy)" [size]="26" />
                <button
                  class="icon-btn"
                  type="button"
                  [attr.aria-label]="'Editar ' + it.name"
                  (click)="openItem(it)"
                >
                  <app-icon name="edit" [size]="18" />
                </button>
              </li>
            }
          </ul>
        }
      }
    </div>

    <dialog #itemDialog class="sheet" (close)="itemDialog.open || editing.set(null)">
      @if (editing(); as it) {
        <form class="sheet-body" (ngSubmit)="saveItem(it)">
          <h2>Editar artículo</h2>
          <div class="field">
            <label for="i-name">Nombre</label>
            <input
              id="i-name"
              class="input"
              name="name"
              maxlength="120"
              required
              [(ngModel)]="form.name"
            />
          </div>
          <div class="grid2">
            <div class="field">
              <label for="i-qty">Cantidad</label>
              <input
                id="i-qty"
                class="input"
                name="qty"
                type="number"
                min="0"
                step="any"
                [(ngModel)]="form.quantity"
              />
            </div>
            <div class="field">
              <label for="i-unit">Unidad</label>
              <input
                id="i-unit"
                class="input"
                name="unit"
                maxlength="20"
                placeholder="kg, L, pzas"
                [(ngModel)]="form.unit"
              />
            </div>
          </div>
          <div class="field">
            <label for="i-cat">Categoría o pasillo</label>
            <input
              id="i-cat"
              class="input"
              name="cat"
              maxlength="40"
              placeholder="Lácteos"
              [(ngModel)]="form.category"
            />
          </div>
          <div class="field">
            <label for="i-note">Nota</label>
            <input id="i-note" class="input" name="note" maxlength="500" [(ngModel)]="form.note" />
          </div>
          <p class="hint">Añadido por {{ family.member(it.createdBy).name }}</p>
          <div class="sheet-actions">
            <button class="btn danger" type="button" (click)="removeItem(it)">
              <app-icon name="trash" [size]="18" /> Quitar
            </button>
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="itemDialog.close()">Cancelar</button>
            <button class="btn" type="submit" [disabled]="busy()">Guardar</button>
          </div>
        </form>
      }
    </dialog>

    <dialog #listDialog class="sheet" (close)="listDialog.open || listEditing.set(undefined)">
      @if (listEditing() !== undefined) {
        @let l = listEditing() ?? null;
        <form class="sheet-body" (ngSubmit)="saveList(l)">
          <h2>{{ l ? 'Lista «' + l.name + '»' : 'Nueva lista' }}</h2>
          <div class="field">
            <label for="l-name">Nombre</label>
            <input
              id="l-name"
              class="input"
              name="name"
              maxlength="60"
              placeholder="Súper semanal"
              required
              [(ngModel)]="listName"
            />
          </div>
          <div class="sheet-actions">
            @if (l) {
              <button class="btn ghost" type="button" (click)="archive(l)">Archivar</button>
              @if (family.isAdult()) {
                <button class="btn danger" type="button" (click)="deleteList(l)">Borrar</button>
              }
            }
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="listDialog.close()">Cancelar</button>
            <button class="btn" type="submit" [disabled]="busy() || !listName.trim()">
              {{ l ? 'Guardar' : 'Crear' }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .grow {
      flex: 1;
    }
    .alert {
      align-items: center;
    }
    .add {
      display: flex;
      gap: 8px;
    }
    .add-input {
      position: relative;
      flex: 1;
    }
    .add-btn {
      width: 48px;
      height: 48px;
      border-radius: 12px;
    }
    .add-btn:disabled {
      opacity: 0.5;
    }
    .suggest {
      position: absolute;
      z-index: 3;
      top: calc(100% + 4px);
      left: 0;
      right: 0;
      list-style: none;
      margin: 0;
      padding: 6px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 14px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
    }
    .suggest button {
      width: 100%;
      min-height: 40px;
      border: 0;
      background: none;
      border-radius: 10px;
      padding: 0 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      text-align: left;
    }
    .suggest button:hover {
      background: var(--muted);
    }
    .summary {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 13px;
      color: var(--text-2);
      margin: -8px 0;
    }
    .actions {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .items {
      list-style: none;
      margin: 0;
      padding: 0;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--r-card);
      overflow: hidden;
    }
    .items li {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-right: 6px;
      border-bottom: 1px solid var(--divider);
    }
    .items li:last-child {
      border-bottom: 0;
    }
    .toggle {
      flex: 1;
      min-width: 0;
      min-height: 56px;
      border: 0;
      background: transparent;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 6px 8px 14px;
      text-align: left;
      cursor: pointer;
    }
    .box {
      width: 24px;
      height: 24px;
      border-radius: 8px;
      border: 2px solid var(--check-off);
      background: var(--surface);
      color: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      flex: none;
      transition: background 0.12s;
    }
    li.done .box {
      border-color: var(--primary);
      background: var(--primary);
      color: #fff;
    }
    .text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }
    .name {
      font-size: 15px;
      font-weight: 600;
    }
    li.done .name {
      color: var(--text-2);
      text-decoration: line-through;
    }
    .meta {
      font-size: 12px;
      color: var(--text-2);
    }
    .grid2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
  `,
})
export class Shopping {
  protected readonly store = inject(ShoppingStore);
  protected readonly family = inject(FamilyService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** `/compras/:listId` */
  readonly listId = input<string>();

  protected readonly current = computed<ShoppingList | null>(() => {
    const lists = this.store.lists();
    return lists.find((l) => l.id === this.listId()) ?? lists[0] ?? null;
  });
  protected readonly items = computed(() => {
    const l = this.current();
    return l ? this.store.items(l.id) : [];
  });

  protected readonly draft = signal('');
  protected readonly suggestions = signal<Suggestion[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editing = signal<ShoppingItem | null>(null);
  protected readonly listEditing = signal<ShoppingList | null | undefined>(undefined);
  protected form = { name: '', quantity: null as number | null, unit: '', category: '', note: '' };
  protected listName = '';
  protected readonly meta = itemMeta;

  private readonly itemDialog = viewChild.required<ElementRef<HTMLDialogElement>>('itemDialog');
  private readonly listDialog = viewChild.required<ElementRef<HTMLDialogElement>>('listDialog');
  private suggestTimer: ReturnType<typeof setTimeout> | undefined;
  private suggestSeq = 0;

  constructor() {
    this.store.loadLists().catch((e) => this.error.set(errorMessage(e)));
    effect(() => {
      const l = this.current();
      if (l)
        untracked(() => this.store.loadItems(l.id).catch((e) => this.error.set(errorMessage(e))));
    });
  }

  select(id: string) {
    this.router.navigate(['/compras', id], { replaceUrl: true });
  }

  onDraft(value: string) {
    this.draft.set(value);
    clearTimeout(this.suggestTimer);
    const q = value.trim();
    const list = this.current();
    if (q.length < 2 || !list) {
      this.suggestions.set([]);
      return;
    }
    const seq = ++this.suggestSeq;
    this.suggestTimer = setTimeout(async () => {
      try {
        const res = await this.store.suggestions(q, list.id);
        if (seq === this.suggestSeq) this.suggestions.set(res);
      } catch {
        this.suggestions.set([]);
      }
    }, 200);
  }

  hideSuggestionsSoon() {
    setTimeout(() => this.suggestions.set([]), 150);
  }

  async add() {
    const input = parseQuickAdd(this.draft());
    const list = this.current();
    if (!input || !list) return;
    await this.create(list.id, input);
  }

  async addSuggestion(s: Suggestion) {
    const list = this.current();
    if (!list) return;
    await this.create(list.id, { name: s.name, unit: s.unit, category: s.category });
  }

  private async create(listId: string, input: Parameters<ShoppingStore['addItem']>[1]) {
    this.suggestSeq++;
    this.suggestions.set([]);
    this.draft.set('');
    try {
      await this.store.addItem(listId, input);
    } catch (e) {
      this.draft.set(input.name);
      this.error.set(errorMessage(e));
    }
  }

  async toggle(it: ShoppingItem) {
    try {
      await this.store.toggle(it, this.auth.profile()?.id ?? '');
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  async clearChecked(list: ShoppingList) {
    try {
      await this.store.clearChecked(list.id);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  openItem(it: ShoppingItem) {
    this.form = {
      name: it.name,
      quantity: it.quantity,
      unit: it.unit ?? '',
      category: it.category ?? '',
      note: it.note ?? '',
    };
    this.editing.set(it);
    this.itemDialog().nativeElement.showModal();
  }

  async saveItem(it: ShoppingItem) {
    if (!this.form.name.trim()) return;
    await this.inDialog(this.itemDialog(), () =>
      this.store.editItem(it, {
        name: this.form.name.trim(),
        quantity: this.form.quantity || null,
        unit: this.form.unit.trim() || null,
        category: this.form.category.trim() || null,
        note: this.form.note.trim() || null,
      }),
    );
  }

  async removeItem(it: ShoppingItem) {
    await this.inDialog(this.itemDialog(), () => this.store.removeItem(it));
  }

  openList(l: ShoppingList | null) {
    this.listName = l?.name ?? '';
    this.listEditing.set(l);
    this.listDialog().nativeElement.showModal();
  }

  async saveList(l: ShoppingList | null) {
    const name = this.listName.trim();
    if (!name) return;
    await this.inDialog(this.listDialog(), async () => {
      if (l) {
        await this.store.renameList(l, name);
      } else {
        const created = await this.store.createList(name);
        this.select(created.id);
      }
    });
  }

  async archive(l: ShoppingList) {
    await this.inDialog(this.listDialog(), () => this.store.archiveList(l));
    this.router.navigate(['/compras'], { replaceUrl: true });
  }

  async deleteList(l: ShoppingList) {
    if (!confirm(`¿Borrar la lista «${l.name}» y sus artículos?`)) return;
    await this.inDialog(this.listDialog(), () => this.store.deleteList(l.id));
    this.router.navigate(['/compras'], { replaceUrl: true });
  }

  private async inDialog(dialog: ElementRef<HTMLDialogElement>, action: () => Promise<void>) {
    this.busy.set(true);
    try {
      await action();
      dialog.nativeElement.close();
    } catch (e) {
      dialog.nativeElement.close();
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

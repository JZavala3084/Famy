import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { fromLocalInput, toLocalInput } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { Chore, ChoreInput } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { ChoresStore } from './chores.store';
import { RepeatKind, WEEKDAYS, buildRrule, parseRepeat } from './rrule';

/** Diálogo para crear o editar una tarea (solo adultos). */
@Component({
  selector: 'app-chore-form',
  imports: [FormsModule, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="sheet" (close)="dialog.open || open.set(false)">
      @if (open()) {
        <form class="sheet-body" (ngSubmit)="save()">
          <h2>{{ chore() ? 'Editar tarea' : 'Nueva tarea' }}</h2>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <div class="field">
            <label for="c-title">Tarea</label>
            <input
              id="c-title"
              class="input"
              name="title"
              maxlength="120"
              placeholder="Sacar la basura"
              required
              [(ngModel)]="f.title"
            />
          </div>
          <div class="field">
            <label for="c-desc">Detalles (opcional)</label>
            <textarea
              id="c-desc"
              class="input"
              name="desc"
              maxlength="1000"
              rows="2"
              [(ngModel)]="f.description"
            ></textarea>
          </div>
          <div class="grid2">
            <div class="field">
              <label for="c-due">Fecha y hora</label>
              <input
                id="c-due"
                class="input"
                name="due"
                type="datetime-local"
                [(ngModel)]="f.due"
              />
            </div>
            <div class="field">
              <label for="c-points">Puntos</label>
              <input
                id="c-points"
                class="input"
                name="points"
                type="number"
                min="0"
                max="1000"
                [(ngModel)]="f.points"
              />
            </div>
          </div>
          <div class="field">
            <label for="c-repeat">Se repite</label>
            <select id="c-repeat" class="input" name="repeat" [(ngModel)]="f.repeat">
              <option value="none">No se repite</option>
              <option value="daily">Todos los días</option>
              <option value="weekly">Días específicos de la semana</option>
              <option value="monthly">Cada mes</option>
              @if (f.repeat === 'custom') {
                <option value="custom">Personalizada ({{ f.raw }})</option>
              }
            </select>
          </div>
          @if (f.repeat === 'weekly') {
            <div class="days" role="group" aria-label="Días">
              @for (d of weekdays; track d[0]) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="f.days.includes(d[0])"
                  [attr.aria-pressed]="f.days.includes(d[0])"
                  (click)="toggleDay(d[0])"
                >
                  {{ d[1] }}
                </button>
              }
            </div>
          }
          @if (f.repeat !== 'none' && !f.due) {
            <p class="hint">Las tareas que se repiten necesitan la fecha de la primera vez.</p>
          }

          <label class="switch">
            <input
              type="checkbox"
              name="rotation"
              [(ngModel)]="f.rotation"
              [disabled]="f.repeat === 'none'"
            />
            <span>
              <b>Rotar entre miembros</b>
              <span class="hint">Cada vez que se marca, pasa al siguiente.</span>
            </span>
          </label>

          @if (f.rotation && f.repeat !== 'none') {
            <div class="field">
              <span class="label">Orden de la rotación</span>
              <div class="people">
                @for (m of family.members(); track m.id) {
                  <button
                    type="button"
                    class="person"
                    [class.on]="f.order.includes(m.id)"
                    (click)="toggleOrder(m.id)"
                  >
                    <app-avatar [member]="m" [size]="26" />
                    {{ m.name }}
                    @if (f.order.indexOf(m.id) >= 0) {
                      <span class="pos">{{ f.order.indexOf(m.id) + 1 }}</span>
                    }
                  </button>
                }
              </div>
            </div>
          } @else {
            <div class="field">
              <label for="c-who">Responsable</label>
              <select id="c-who" class="input" name="assignee" [(ngModel)]="f.assigneeId">
                <option value="">Sin asignar</option>
                @for (m of family.members(); track m.id) {
                  <option [value]="m.id">{{ m.name }}</option>
                }
              </select>
            </div>
          }

          <div class="sheet-actions">
            @if (chore(); as c) {
              <button class="btn danger" type="button" (click)="remove(c)" [disabled]="busy()">
                <app-icon name="trash" [size]="18" /> Borrar
              </button>
            }
            <span class="grow"></span>
            <button class="btn ghost" type="button" (click)="close()">Cancelar</button>
            <button class="btn" type="submit" [disabled]="busy() || !f.title.trim()">
              Guardar
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .grid2 {
      display: grid;
      grid-template-columns: 1.4fr 1fr;
      gap: 12px;
    }
    .days,
    .people {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .switch {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      cursor: pointer;
    }
    .switch input {
      width: 20px;
      height: 20px;
      margin: 2px 0 0;
      accent-color: var(--primary);
    }
    .switch > span {
      display: flex;
      flex-direction: column;
      font-size: 14px;
    }
    .person {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: 40px;
      padding: 4px 12px 4px 6px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface);
      font-size: 14px;
      cursor: pointer;
    }
    .person.on {
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .pos {
      font-size: 12px;
      font-weight: 800;
      color: var(--primary);
    }
    .grow {
      flex: 1;
    }
  `,
})
export class ChoreForm {
  protected readonly family = inject(FamilyService);
  private readonly store = inject(ChoresStore);
  readonly saved = output<void>();

  protected readonly weekdays = WEEKDAYS;
  protected readonly open = signal(false);
  protected readonly chore = signal<Chore | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected f = this.blank();
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  show(chore: Chore | null) {
    this.chore.set(chore);
    this.error.set(null);
    if (chore) {
      const r = parseRepeat(chore.rrule);
      this.f = {
        title: chore.title,
        description: chore.description ?? '',
        due: toLocalInput(chore.dueAt),
        points: chore.points,
        repeat: r.kind,
        days: r.days,
        raw: r.raw,
        rotation: chore.rotation,
        order: chore.rotationOrder ?? [],
        assigneeId: chore.assigneeId ?? '',
      };
    } else {
      this.f = this.blank();
    }
    this.open.set(true);
    this.dialog().nativeElement.showModal();
  }

  close() {
    this.dialog().nativeElement.close();
  }

  toggleDay(d: string) {
    this.f.days = this.f.days.includes(d)
      ? this.f.days.filter((x) => x !== d)
      : [...this.f.days, d];
  }

  toggleOrder(id: string) {
    this.f.order = this.f.order.includes(id)
      ? this.f.order.filter((x) => x !== id)
      : [...this.f.order, id];
  }

  async save() {
    const f = this.f;
    const rrule = buildRrule({ kind: f.repeat, days: f.days, raw: f.raw });
    if (rrule && !f.due) {
      this.error.set('Elige la fecha y hora de la primera vez.');
      return;
    }
    const rotation = !!rrule && f.rotation;
    if (rotation && f.order.length < 2) {
      this.error.set('Elige al menos dos personas para la rotación.');
      return;
    }
    const input: ChoreInput = {
      title: f.title.trim(),
      description: f.description.trim() || null,
      dueAt: fromLocalInput(f.due),
      rrule,
      points: Number(f.points) || 0,
      rotation,
      rotationOrder: rotation ? f.order : [],
      assigneeId: rotation ? f.order[0] : f.assigneeId || null,
    };
    const current = this.chore();
    if (current && rotation && current.assigneeId && f.order.includes(current.assigneeId)) {
      // Al editar no se reinicia el turno actual.
      input.assigneeId = current.assigneeId;
    }
    await this.run(async () => {
      if (current) await this.store.update(current, input);
      else await this.store.create(input);
    });
  }

  async remove(c: Chore) {
    if (!confirm(`¿Borrar «${c.title}»?`)) return;
    await this.run(() => this.store.remove(c));
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.close();
      this.saved.emit();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  private blank() {
    return {
      title: '',
      description: '',
      due: '',
      points: 1,
      repeat: 'none' as RepeatKind,
      days: [] as string[],
      raw: null as string | null,
      rotation: false,
      order: [] as string[],
      assigneeId: '',
    };
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from '../../core/config';
import { addDays, dayKey, weekStart } from '../../core/dates';
import { FamilyService } from '../../core/family.service';
import { ChangeEvent, Chore, ChoreCompletion, ChoreInput, Scoreboard } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { removeById, upsert } from '../../core/versioned';

/**
 * Tareas de hoy: pendientes con fecha hasta hoy (incluye atrasadas) o sin fecha, más las que se marcaron hoy.
 * Una tarea que se repite avanza a su siguiente fecha al marcarse; por eso «hecha hoy» sale de las marcas del día
 * (`doneToday`) y no del estado de la tarea.
 */
export function choresForToday(
  chores: readonly Chore[],
  today: string,
  tz: string,
  doneToday: ReadonlySet<string>,
): Chore[] {
  return chores
    .filter((c) => {
      if (doneToday.has(c.id)) return true;
      if (c.done) return false;
      return !c.dueAt || dayKey(c.dueAt, tz) <= today;
    })
    .sort(byDue);
}

export function byDue(a: Chore, b: Chore): number {
  if (!a.dueAt) return b.dueAt ? 1 : a.title.localeCompare(b.title);
  if (!b.dueAt) return -1;
  return a.dueAt.localeCompare(b.dueAt);
}

/**
 * Estado de Tareas del hogar, sincronizado con los eventos `chore` y `chore.completion`: las tareas (incluidas las
 * hechas), las marcas de hoy y el puntaje de la semana.
 */
@Injectable({ providedIn: 'root' })
export class ChoresStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly family = inject(FamilyService);
  private readonly realtime = inject(RealtimeService);

  private familyId: string | null = null;
  private loading = false;
  private buffered: ChangeEvent[] = [];
  private scoreTimer: ReturnType<typeof setTimeout> | undefined;
  private completionsDay: string | null = null;

  private readonly state = signal<Chore[] | null>(null);
  /** Marcas de hoy (más reciente primero). */
  private readonly completions = signal<ChoreCompletion[]>([]);
  readonly scoreboard = signal<Scoreboard | null>(null);
  /** Se actualiza cada minuto para que «hoy» cambie a medianoche. */
  private readonly clock = signal(Date.now());

  readonly loaded = computed(() => this.state() !== null);
  readonly all = computed(() => [...(this.state() ?? [])].sort(byDue));
  readonly today = computed(() => {
    this.clock();
    return dayKey(new Date(), this.family.timezone());
  });
  readonly doneTodayIds = computed(() => new Set(this.completions().map((c) => c.choreId)));
  readonly todayList = computed(() =>
    choresForToday(this.state() ?? [], this.today(), this.family.timezone(), this.doneTodayIds()),
  );
  readonly todayDone = computed(
    () => this.todayList().filter((c) => this.doneTodayIds().has(c.id)).length,
  );
  readonly weekEnd = computed(() => addDays(weekStart(this.today()), 6));

  constructor() {
    this.realtime.events.subscribe((e) => this.onEvent(e));
    this.realtime.resync.subscribe(() => this.load(true).catch(() => undefined));
    setInterval(() => {
      this.clock.set(Date.now());
      // Cambió el día: las marcas de «hoy» y el puntaje se vuelven a pedir.
      if (this.familyId && this.completionsDay && this.completionsDay !== this.today()) {
        this.load(true).catch(() => undefined);
      }
    }, 60_000);
  }

  async load(force = false): Promise<void> {
    const fid = this.ensureFamily();
    if (!force && this.state() !== null) return;
    await this.realtime.ready;
    this.loading = true;
    const day = this.today();
    try {
      const [chores, done, board] = await Promise.all([
        firstValueFrom(this.http.get<Chore[]>(this.url(''), { params: { includeDone: true } })),
        firstValueFrom(
          this.http.get<ChoreCompletion[]>(this.url('/completions'), { params: { date: day } }),
        ),
        firstValueFrom(this.http.get<Scoreboard>(this.url('/scoreboard'))),
      ]);
      if (fid === this.familyId) {
        this.state.set(chores);
        this.completions.set(done);
        this.completionsDay = day;
        this.scoreboard.set(board);
      }
    } finally {
      this.loading = false;
      const pending = this.buffered;
      this.buffered = [];
      pending.forEach((e) => this.apply(e));
    }
  }

  isDoneToday(c: Chore): boolean {
    return this.doneTodayIds().has(c.id);
  }

  /** Quién la hizo hoy y a qué hora (la marca más reciente). */
  completionToday(c: Chore): ChoreCompletion | null {
    return this.completions().find((x) => x.choreId === c.id) ?? null;
  }

  async create(input: ChoreInput): Promise<void> {
    this.applyChore(await firstValueFrom(this.http.post<Chore>(this.url(''), input)));
  }

  async update(chore: Chore, input: ChoreInput): Promise<void> {
    this.applyChore(
      await firstValueFrom(
        this.http.put<Chore>(this.url(`/${chore.id}`), { ...input, version: chore.version }),
      ),
    );
  }

  async remove(chore: Chore): Promise<void> {
    await firstValueFrom(this.http.delete(this.url(`/${chore.id}`)));
    this.state.update((s) => (s ? removeById(s, chore.id) : s));
  }

  async complete(chore: Chore, memberId?: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<{ chore: Chore; completion: ChoreCompletion }>(
        this.url(`/${chore.id}/complete`),
        { memberId: memberId ?? null, version: chore.version },
      ),
    );
    this.applyChore(res.chore);
    this.addCompletion(res.completion);
    this.refreshScoreboardSoon();
  }

  /** Deshace la última marca (el backend solo permite deshacer la más reciente de cada tarea). */
  async undo(chore: Chore): Promise<void> {
    let last = this.completionToday(chore);
    if (!last) {
      [last] = await firstValueFrom(
        this.http.get<ChoreCompletion[]>(this.url(`/${chore.id}/completions`), {
          params: { limit: 1 },
        }),
      );
    }
    if (!last) return;
    const restored = await firstValueFrom(
      this.http.delete<Chore>(this.url(`/${chore.id}/completions/${last.id}`)),
    );
    this.applyChore(restored);
    this.dropCompletion(last.id);
    this.refreshScoreboardSoon();
  }

  private onEvent(e: ChangeEvent): void {
    if (!e.entity.startsWith('chore') || e.familyId !== this.familyId) return;
    if (this.loading) {
      this.buffered.push(e);
      return;
    }
    this.apply(e);
  }

  private apply(e: ChangeEvent): void {
    if (e.entity === 'chore') {
      if (e.op === 'D') this.state.update((s) => (s ? removeById(s, e.entityId) : s));
      else this.applyChore(e.payload as Chore);
    } else if (e.entity === 'chore.completion') {
      if (e.op === 'D') this.dropCompletion(e.entityId);
      else this.addCompletion(e.payload as ChoreCompletion);
      this.refreshScoreboardSoon();
    }
  }

  private applyChore(c: Chore): void {
    this.state.update((s) => (s ? upsert(s, c) : s));
  }

  private addCompletion(c: ChoreCompletion): void {
    if (dayKey(c.completedAt, this.family.timezone()) !== this.completionsDay) return;
    this.completions.update((list) =>
      list.some((x) => x.id === c.id)
        ? list
        : [c, ...list].sort((a, b) => b.completedAt.localeCompare(a.completedAt)),
    );
  }

  private dropCompletion(id: string): void {
    this.completions.update((list) => removeById(list, id));
  }

  private refreshScoreboardSoon(): void {
    clearTimeout(this.scoreTimer);
    this.scoreTimer = setTimeout(async () => {
      if (!this.familyId) return;
      try {
        this.scoreboard.set(
          await firstValueFrom(this.http.get<Scoreboard>(this.url('/scoreboard'))),
        );
      } catch {
        // Se reintenta con el siguiente cambio.
      }
    }, 400);
  }

  private ensureFamily(): string {
    const fid = this.family.current()!.id;
    if (fid !== this.familyId) {
      this.familyId = fid;
      this.state.set(null);
      this.completions.set([]);
      this.completionsDay = null;
      this.scoreboard.set(null);
      this.buffered = [];
    }
    return fid;
  }

  private url(path: string): string {
    return this.config.api(`/families/${this.familyId}/chores${path}`);
  }
}

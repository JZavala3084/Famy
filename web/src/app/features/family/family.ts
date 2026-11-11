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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import QRCode from 'qrcode';
import { AuthService } from '../../core/auth.service';
import { formatDay } from '../../core/dates';
import { FamilyService, MemberInfo, ROLE_LABELS } from '../../core/family.service';
import { FamilyRole, Invitation, JoinRequest } from '../../core/models';
import { errorMessage } from '../../core/problem';
import { RealtimeService } from '../../core/realtime.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { CURRENCIES } from '../onboarding/onboarding';

@Component({
  selector: 'app-family',
  imports: [FormsModule, Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div class="col">
          <h1>{{ family.current()?.name }}</h1>
          <p class="muted">
            {{ family.members().length }}
            {{ family.members().length === 1 ? 'miembro' : 'miembros' }} · tu rol:
            {{ roleLabel(family.myRole()) }}
          </p>
        </div>
        @if (family.canInvite()) {
          <button class="btn" type="button" (click)="openInvite()">
            <app-icon name="userPlus" [size]="18" /> Invitar
          </button>
        }
      </header>

      @if (welcome()) {
        <div class="alert info fade-in">
          <app-icon name="check" />
          <span
            >¡Listo, tu familia está creada! Comparte el código o el QR para que los demás se
            unan.</span
          >
        </div>
      }
      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }

      @if (requests().length) {
        <section class="card">
          <div class="card-head">
            <h2>Solicitudes para unirse</h2>
            <span class="pill accent">{{ requests().length }}</span>
          </div>
          <ul class="rows">
            @for (r of requests(); track r.id) {
              <li>
                <span class="col grow">
                  <b>{{ r.displayName || r.email }}</b>
                  <span class="muted small"
                    >{{ r.email }} · como {{ roleLabel(r.role).toLowerCase() }}</span
                  >
                </span>
                <button
                  class="btn ghost small"
                  type="button"
                  (click)="decide(r, false)"
                  [disabled]="busy()"
                >
                  Rechazar
                </button>
                <button
                  class="btn small"
                  type="button"
                  (click)="decide(r, true)"
                  [disabled]="busy()"
                >
                  Aprobar
                </button>
              </li>
            }
          </ul>
        </section>
      }

      <section class="card">
        <div class="card-head"><h2>Miembros</h2></div>
        <ul class="rows">
          @for (m of family.members(); track m.id) {
            <li>
              <app-avatar [member]="m" [size]="40" />
              <span class="col grow">
                <b
                  >{{ m.name }}
                  @if (m.id === meId()) {
                    <span class="muted">(tú)</span>
                  }
                </b>
                <span class="muted small">{{ roleLabel(m.role) }}</span>
              </span>
              @if (canManage(m)) {
                <label class="sr-only" [for]="'role-' + m.id">Rol de {{ m.name }}</label>
                <select
                  class="input role"
                  [id]="'role-' + m.id"
                  [ngModel]="m.role"
                  (ngModelChange)="changeRole(m, $event)"
                >
                  @for (r of assignableRoles; track r) {
                    <option [value]="r">{{ roleLabel(r) }}</option>
                  }
                </select>
                <button
                  class="icon-btn"
                  type="button"
                  [attr.aria-label]="'Quitar a ' + m.name"
                  (click)="remove(m)"
                >
                  <app-icon name="trash" [size]="18" />
                </button>
              }
            </li>
          }
        </ul>
        @if (family.myRole() !== 'OWNER') {
          <button class="link-btn leave" type="button" (click)="leave()">
            Salir de la familia
          </button>
        }
      </section>

      @if (family.canInvite() && invitations().length) {
        <section class="card">
          <div class="card-head"><h2>Invitaciones activas</h2></div>
          <ul class="rows">
            @for (i of invitations(); track i.id) {
              <li>
                <span class="col grow">
                  <b class="mono">{{ i.code }}</b>
                  <span class="muted small">
                    {{ i.email ? 'Para ' + i.email + ' · ' : '' }}{{ roleLabel(i.role) }} ·
                    {{ i.uses }} {{ i.uses === 1 ? 'uso' : 'usos' }} · vence {{ day(i.expiresAt) }}
                  </span>
                </span>
                <button
                  class="icon-btn"
                  type="button"
                  (click)="copy(i.code)"
                  [attr.aria-label]="'Copiar ' + i.code"
                >
                  <app-icon name="copy" [size]="18" />
                </button>
                <button
                  class="icon-btn"
                  type="button"
                  (click)="revoke(i)"
                  [attr.aria-label]="'Revocar ' + i.code"
                >
                  <app-icon name="x" [size]="18" />
                </button>
              </li>
            }
          </ul>
        </section>
      }

      @if (family.isManager()) {
        <section class="card">
          <form class="settings" (ngSubmit)="saveFamily()">
            <h2>Datos de la familia</h2>
            <div class="field">
              <label for="f-name">Nombre</label>
              <input
                id="f-name"
                class="input"
                name="name"
                maxlength="80"
                [(ngModel)]="famName"
                [disabled]="family.myRole() !== 'OWNER'"
              />
            </div>
            <div class="field">
              <label for="f-cur">Moneda del presupuesto familiar</label>
              <select id="f-cur" class="input" name="currency" [(ngModel)]="famCurrency">
                @for (c of currencies; track c[0]) {
                  <option [value]="c[0]">{{ c[0] }} · {{ c[1] }}</option>
                }
              </select>
            </div>
            @if (family.myRole() === 'OWNER') {
              <label class="switch">
                <input type="checkbox" name="mci" [(ngModel)]="famMembersCanInvite" />
                <span>Los miembros adultos también pueden invitar</span>
              </label>
            }
            <div class="sheet-actions">
              <button class="btn" type="submit" [disabled]="busy()">Guardar</button>
            </div>
          </form>
        </section>
      }
    </div>

    <dialog #inviteDialog class="sheet">
      <div class="sheet-body">
        <div class="card-head">
          <h2>Invitar a la familia</h2>
          <button class="icon-btn" type="button" aria-label="Cerrar" (click)="inviteDialog.close()">
            <app-icon name="x" />
          </button>
        </div>
        @if (created(); as inv) {
          <div class="share fade-in">
            @if (qr()) {
              <img [src]="qr()" width="200" height="200" alt="Código QR de la invitación" />
            }
            <p class="muted small">Código de invitación</p>
            <p class="code">{{ inv.code }}</p>
            <div class="share-actions">
              <button class="btn ghost small" type="button" (click)="copy(inv.code)">
                <app-icon name="copy" [size]="16" /> Copiar código
              </button>
              @if (inv.link) {
                <button class="btn ghost small" type="button" (click)="copy(inv.link)">
                  <app-icon name="copy" [size]="16" /> Copiar enlace
                </button>
              }
            </div>
            <p class="hint center">
              {{
                inv.requiresApproval
                  ? 'Tendrás que aprobar a quien se una con este código.'
                  : 'Entrará sin necesidad de aprobación.'
              }}
              Vence {{ day(inv.expiresAt) }}.
            </p>
            @if (copied()) {
              <p class="pill primary">Copiado</p>
            }
          </div>
          <button class="btn ghost" type="button" (click)="created.set(null)">
            Crear otra invitación
          </button>
        } @else {
          <form class="settings" (ngSubmit)="createInvite()">
            <div class="field">
              <label for="inv-role">Entra como</label>
              <select id="inv-role" class="input" name="role" [(ngModel)]="invRole">
                @for (r of invitableRoles(); track r) {
                  <option [value]="r">{{ roleLabel(r) }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="inv-email">Correo (opcional)</label>
              <input
                id="inv-email"
                class="input"
                name="email"
                type="email"
                placeholder="ana@correo.com"
                [(ngModel)]="invEmail"
              />
              <p class="hint">
                Con correo le enviamos la invitación y entra sin aprobación. Sin correo obtienes un
                código y un QR para compartir.
              </p>
            </div>
            <div class="field">
              <label for="inv-days">Vigencia</label>
              <select id="inv-days" class="input" name="days" [(ngModel)]="invDays">
                <option [ngValue]="1">1 día</option>
                <option [ngValue]="7">7 días</option>
                <option [ngValue]="30">30 días</option>
              </select>
            </div>
            <button class="btn block" type="submit" [disabled]="busy()">Crear invitación</button>
          </form>
        }
      </div>
    </dialog>
  `,
  styles: `
    .col {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .rows {
      list-style: none;
      margin: 12px 0 0;
      padding: 0;
      display: flex;
      flex-direction: column;
    }
    .rows li {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid var(--divider);
      flex-wrap: wrap;
    }
    .rows li:last-child {
      border-bottom: 0;
    }
    .role {
      width: auto;
      min-height: 40px;
    }
    .leave {
      margin-top: 12px;
      color: var(--accent-strong);
    }
    .mono {
      font-variant-numeric: tabular-nums;
      letter-spacing: 0.04em;
    }
    .settings {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .switch {
      display: flex;
      gap: 10px;
      align-items: center;
      font-size: 14px;
    }
    .switch input {
      width: 20px;
      height: 20px;
      accent-color: var(--primary);
    }
    .share {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }
    .share img {
      border-radius: 12px;
      border: 1px solid var(--border);
    }
    .code {
      font-size: 26px;
      font-weight: 800;
      letter-spacing: 0.08em;
    }
    .share-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: center;
    }
    .center {
      text-align: center;
    }
  `,
})
export class FamilyPage {
  protected readonly family = inject(FamilyService);
  private readonly auth = inject(AuthService);
  private readonly realtime = inject(RealtimeService);
  private readonly router = inject(Router);

  readonly nueva = input<string>();
  readonly invitar = input<string>();

  protected readonly currencies = CURRENCIES;
  protected readonly assignableRoles: FamilyRole[] = ['ADMIN', 'MEMBER', 'CHILD'];
  protected readonly invitableRoles = computed<FamilyRole[]>(() =>
    this.family.isManager() ? ['MEMBER', 'CHILD', 'ADMIN'] : ['MEMBER', 'CHILD'],
  );
  protected readonly meId = computed(() => this.auth.profile()?.id);
  protected readonly welcome = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly requests = signal<JoinRequest[]>([]);
  protected readonly invitations = signal<Invitation[]>([]);
  protected readonly created = signal<Invitation | null>(null);
  protected readonly qr = signal<string | null>(null);
  protected readonly copied = signal(false);

  protected famName = '';
  protected famCurrency = 'USD';
  protected famMembersCanInvite = false;
  protected invRole: FamilyRole = 'MEMBER';
  protected invEmail = '';
  protected invDays = 7;

  private readonly inviteDialog = viewChild.required<ElementRef<HTMLDialogElement>>('inviteDialog');

  constructor() {
    effect(() => {
      const f = this.family.current();
      if (!f) return;
      untracked(() => {
        this.famName = f.name;
        this.famCurrency = f.currency;
        this.famMembersCanInvite = f.membersCanInvite;
        this.loadAdmin();
      });
    });
    // Nuevos miembros o solicitudes llegan como family.member.
    this.realtime.events.pipe(takeUntilDestroyed()).subscribe((e) => {
      if (e.entity === 'family.member') this.loadAdmin();
    });
    queueMicrotask(() => {
      const nueva = !!this.nueva();
      if ((nueva || this.invitar()) && this.family.canInvite()) {
        this.welcome.set(nueva);
        this.openInvite();
        if (nueva) this.createInvite();
        // Quitar ?nueva / ?invitar para que recargar no cree otra invitación.
        this.router.navigate([], { queryParams: {}, replaceUrl: true });
      }
    });
  }

  protected roleLabel(r: FamilyRole | null) {
    return r ? ROLE_LABELS[r] : '';
  }

  protected day(i: string) {
    return formatDay(i, this.family.timezone());
  }

  protected canManage(m: MemberInfo) {
    return this.family.isManager() && m.role !== 'OWNER' && m.id !== this.meId();
  }

  private async loadAdmin() {
    try {
      if (this.family.isManager()) this.requests.set(await this.family.joinRequests());
      if (this.family.canInvite()) this.invitations.set(await this.family.invitations());
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  openInvite() {
    this.copied.set(false);
    this.inviteDialog().nativeElement.showModal();
  }

  async createInvite() {
    await this.run(async () => {
      const email = this.invEmail.trim();
      const inv = await this.family.createInvitation({
        role: this.invRole,
        email: email || undefined,
        expiresInDays: this.invDays,
      });
      this.created.set(inv);
      this.qr.set(
        inv.link
          ? await QRCode.toDataURL(inv.link, { width: 400, margin: 1, color: { dark: '#1B1D22' } })
          : null,
      );
      this.invEmail = '';
      this.invitations.update((l) => [inv, ...l]);
    });
  }

  async revoke(i: Invitation) {
    if (!confirm(`¿Revocar la invitación ${i.code}?`)) return;
    await this.run(async () => {
      await this.family.revokeInvitation(i.id);
      this.invitations.update((l) => l.filter((x) => x.id !== i.id));
    });
  }

  async copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      prompt('Copia el texto:', text);
    }
  }

  async decide(r: JoinRequest, approve: boolean) {
    await this.run(async () => {
      await this.family.decide(r.id, approve);
      this.requests.update((l) => l.filter((x) => x.id !== r.id));
    });
  }

  async changeRole(m: MemberInfo, role: FamilyRole) {
    if (role === m.role) return;
    await this.run(() => this.family.changeRole(m.id, role));
  }

  async remove(m: MemberInfo) {
    if (!confirm(`¿Quitar a ${m.name} de la familia?`)) return;
    await this.run(() => this.family.removeMember(m.id));
  }

  async leave() {
    const id = this.meId();
    if (!id || !confirm('¿Salir de la familia? Dejarás de ver su información.')) return;
    await this.run(async () => {
      this.realtime.stop();
      await this.family.removeMember(id);
      await this.router.navigateByUrl('/bienvenida');
    });
  }

  async saveFamily() {
    const f = this.family.current()!;
    const changes: Parameters<FamilyService['update']>[0] = {};
    if (this.famCurrency !== f.currency) changes.currency = this.famCurrency;
    if (this.family.myRole() === 'OWNER') {
      if (this.famName.trim() && this.famName.trim() !== f.name) changes.name = this.famName.trim();
      if (this.famMembersCanInvite !== f.membersCanInvite)
        changes.membersCanInvite = this.famMembersCanInvite;
    }
    if (!Object.keys(changes).length) return;
    await this.run(() => this.family.update(changes));
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set(errorMessage(e));
      this.inviteDialog().nativeElement.close();
    } finally {
      this.busy.set(false);
    }
  }
}

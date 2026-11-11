import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FamilyService, ROLE_LABELS } from '../../core/family.service';
import { InvitationPreview } from '../../core/models';
import { errorMessage, problemOf } from '../../core/problem';
import { Icon } from '../../shared/icon';
import { AuthCard } from '../auth/auth-card';

export const CURRENCIES = [
  ['USD', 'Dólar estadounidense'],
  ['MXN', 'Peso mexicano'],
  ['PEN', 'Sol peruano'],
  ['EUR', 'Euro'],
  ['COP', 'Peso colombiano'],
  ['CLP', 'Peso chileno'],
  ['ARS', 'Peso argentino'],
  ['GTQ', 'Quetzal'],
] as const;

/** Normaliza lo que escribe o pega el usuario: «fam-fm7k-2qab» → «FM7K2QAB». */
export function normalizeCode(raw: string): string {
  const s = raw.toUpperCase().replace(/[^A-Z0-9]/g, '');
  return (s.length === 11 && s.startsWith('FAM') ? s.slice(3) : s).slice(0, 8);
}

/** Paso 2 de 2: crear una familia o unirse con un código (o con el enlace `/join/:token`). */
@Component({
  selector: 'app-onboarding',
  imports: [AuthCard, FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (pending()) {
      <app-auth-card
        heading="Solicitud enviada"
        subtitle="El administrador de la familia aprobará tu solicitud antes de que veas la información."
      >
        <div class="alert info">
          <app-icon name="clock" /> Te avisaremos aquí en cuanto te acepten.
        </div>
        <button class="btn block" type="button" (click)="checkAgain()" [disabled]="busy()">
          Revisar de nuevo
        </button>
        @if (error()) {
          <p class="hint center">{{ error() }}</p>
        }
        <button class="link-btn center" type="button" (click)="logout()">Cerrar sesión</button>
      </app-auth-card>
    } @else {
      <app-auth-card
        heading="Conecta con tu familia"
        subtitle="Crea un espacio nuevo o únete con el código que te compartieron."
      >
        <p class="step">Paso 2 de 2</p>
        @if (!verified()) {
          <div class="alert warn">
            <span>
              Confirma tu correo ({{ email() }}) para crear o unirte a una familia.
              <button class="link-btn" type="button" (click)="resend()" [disabled]="busy()">
                Reenviar correo
              </button>
              @if (resent()) {
                · Enviado
              }
              ·
              <button class="link-btn" type="button" (click)="reloadProfile()">
                Ya lo confirmé
              </button>
            </span>
          </div>
        }
        @if (!preview()) {
          <div class="segmented" role="tablist">
            <button
              type="button"
              role="tab"
              [attr.aria-selected]="mode() === 'create'"
              (click)="mode.set('create')"
            >
              Crear familia
            </button>
            <button
              type="button"
              role="tab"
              [attr.aria-selected]="mode() === 'join'"
              (click)="mode.set('join')"
            >
              Unirme
            </button>
          </div>
        }
        @if (error()) {
          <div class="alert error" role="alert">{{ error() }}</div>
        }

        @if (preview(); as p) {
          <section class="invite fade-in">
            <span class="fam-avatar">{{ p.familyName.charAt(0) }}</span>
            <div>
              <h2>{{ p.familyName }}</h2>
              <p class="muted small">
                {{ p.memberCount }} {{ p.memberCount === 1 ? 'miembro' : 'miembros' }} · entrarás
                como
                {{ roleLabel(p.role) }}
              </p>
            </div>
          </section>
          @if (p.requiresApproval) {
            <p class="hint">Un administrador aprobará tu solicitud.</p>
          }
          <button
            class="btn block"
            type="button"
            (click)="join()"
            [disabled]="busy() || !verified()"
          >
            {{ p.requiresApproval ? 'Solicitar unirme' : 'Unirme a la familia' }}
          </button>
          @if (!token()) {
            <button class="link-btn center" type="button" (click)="preview.set(null)">
              Usar otro código
            </button>
          }
        } @else if (mode() === 'create') {
          <form class="form" (ngSubmit)="create()">
            <div class="field">
              <label for="fam">Nombre de la familia</label>
              <input
                id="fam"
                class="input"
                name="name"
                maxlength="80"
                placeholder="Familia Zavala"
                [(ngModel)]="name"
              />
            </div>
            <div class="field">
              <label for="famcur">Moneda del presupuesto familiar</label>
              <select id="famcur" class="input" name="currency" [(ngModel)]="currency">
                @for (c of currencies; track c[0]) {
                  <option [value]="c[0]">{{ c[0] }} · {{ c[1] }}</option>
                }
              </select>
            </div>
            <div class="alert info">
              <app-icon name="shield" />
              Serás administrador. Al crearla recibirás un código y un QR para invitar a los demás.
            </div>
            <button class="btn block" type="submit" [disabled]="busy() || !verified()">
              Crear familia
            </button>
          </form>
        } @else {
          <form class="form" (ngSubmit)="lookUp()">
            <div class="field">
              <label for="code">Código de invitación</label>
              <input
                id="code"
                class="input code"
                name="code"
                autocomplete="off"
                autocapitalize="characters"
                spellcheck="false"
                placeholder="FAM-XXXX-XXXX"
                [ngModel]="code()"
                (ngModelChange)="code.set(normalize($event))"
              />
              <p class="hint">8 caracteres. Pídelo a quien administra tu familia.</p>
            </div>
            <button class="btn block" type="submit" [disabled]="busy() || code().length !== 8">
              Continuar
            </button>
          </form>
        }
        <button class="link-btn center" type="button" (click)="logout()">Cerrar sesión</button>
      </app-auth-card>
    }
  `,
  styles: `
    .step {
      font-size: 13px;
      font-weight: 700;
      color: var(--primary);
      margin-top: -12px;
      order: -1;
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .code {
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0.25em;
      text-align: center;
      text-transform: uppercase;
    }
    .center {
      align-self: center;
      text-align: center;
    }
    .invite {
      display: flex;
      gap: 14px;
      align-items: center;
      background: var(--primary-soft);
      border-radius: var(--r-card);
      padding: 16px;
    }
    .fam-avatar {
      width: 52px;
      height: 52px;
      border-radius: 16px;
      background: var(--primary);
      color: #fff;
      font-size: 22px;
      font-weight: 800;
      display: flex;
      align-items: center;
      justify-content: center;
      flex: none;
    }
  `,
})
export class Onboarding {
  private readonly auth = inject(AuthService);
  private readonly family = inject(FamilyService);
  private readonly router = inject(Router);

  /** Desde `/join/:token`. */
  readonly token = input<string>();

  protected readonly currencies = CURRENCIES;
  protected readonly mode = signal<'create' | 'join'>('create');
  protected name = '';
  protected currency = this.auth.profile()?.currency ?? 'USD';
  protected readonly code = signal('');
  protected readonly preview = signal<InvitationPreview | null>(null);
  protected readonly pending = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resent = signal(false);
  protected readonly verified = computed(() => !!this.auth.profile()?.emailVerified);
  protected readonly email = computed(() => this.auth.profile()?.email);
  protected readonly normalize = normalizeCode;

  constructor() {
    queueMicrotask(() => {
      const t = this.token();
      if (t) this.lookUp(t);
    });
  }

  protected roleLabel(r: keyof typeof ROLE_LABELS) {
    return ROLE_LABELS[r].toLowerCase();
  }

  async create() {
    if (!this.name.trim()) {
      this.error.set('Escribe el nombre de la familia.');
      return;
    }
    await this.run(async () => {
      await this.family.create(this.name.trim(), this.currency);
      await this.router.navigate(['/familia'], { queryParams: { nueva: 1 } });
    });
  }

  async lookUp(codeOrToken = this.code()) {
    await this.run(async () => {
      try {
        this.preview.set(await this.family.previewInvitation(codeOrToken));
      } catch (e) {
        const p = problemOf(e);
        throw p.status === 404 ? new Error('Ese código no existe o ya venció.') : e;
      }
    });
  }

  async join() {
    const key = this.token() ?? this.code();
    await this.run(async () => {
      const res = await this.family.join(key);
      if (res.status === 'JOINED') {
        await this.router.navigateByUrl('/');
      } else {
        this.pending.set(true);
      }
    });
  }

  async checkAgain() {
    await this.run(async () => {
      await this.auth.refresh();
      const list = await this.family.load();
      if (list.length) {
        await this.router.navigateByUrl('/');
      } else {
        throw new Error('Todavía no te aprueban.');
      }
    });
  }

  async resend() {
    const email = this.email();
    if (!email) return;
    await this.run(async () => {
      await this.auth.resendVerification(email);
      this.resent.set(true);
    });
  }

  async reloadProfile() {
    await this.run(async () => {
      const p = await this.auth.loadProfile();
      if (!p.emailVerified)
        throw new Error('Aún no vemos tu correo confirmado. Abre el enlace del correo.');
    });
  }

  async logout() {
    await this.auth.logout();
    this.family.clear();
    await this.router.navigateByUrl('/login');
  }

  private async run(action: () => Promise<void>) {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set(e instanceof Error && !('status' in e) ? e.message : errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

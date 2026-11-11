import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { AppConfig } from './config';
import {
  Family,
  FamilyRole,
  Invitation,
  InvitationPreview,
  JoinRequest,
  JoinResult,
  Member,
} from './models';

/** Colores de miembro del sistema visual (09 · Diseño UI). */
export const MEMBER_COLORS = ['#1E6B5E', '#C2562E', '#6B4FA0', '#2F5DA8', '#8A6A12'];

export const ROLE_LABELS: Record<FamilyRole, string> = {
  OWNER: 'Propietario',
  ADMIN: 'Administrador',
  MEMBER: 'Miembro',
  CHILD: 'Hijo/a',
};

export interface MemberInfo {
  id: string;
  name: string;
  initial: string;
  color: string;
  role: FamilyRole;
}

@Injectable({ providedIn: 'root' })
export class FamilyService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfig);
  private readonly auth = inject(AuthService);

  readonly families = signal<Family[] | null>(null);
  /** Familia activa: la del token o, si no, la primera (en el MVP cada usuario tiene una). */
  readonly current = computed<Family | null>(() => {
    const list = this.families();
    if (!list?.length) return null;
    const fid = this.auth.familyId();
    return list.find((f) => f.id === fid) ?? list[0];
  });
  readonly timezone = computed(
    () => this.current()?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  );
  readonly myRole = computed(() => this.current()?.myRole ?? null);
  readonly isManager = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  readonly isAdult = computed(() => !!this.myRole() && this.myRole() !== 'CHILD');
  readonly canInvite = computed(
    () => this.isManager() || (this.isAdult() && !!this.current()?.membersCanInvite),
  );

  readonly members = computed<MemberInfo[]>(() =>
    (this.current()?.members ?? []).map((m, i) => memberInfo(m, i)),
  );
  readonly memberById = computed(() => new Map(this.members().map((m) => [m.id, m])));

  member(id: string | null | undefined): MemberInfo {
    return (
      (id && this.memberById().get(id)) || {
        id: id ?? '',
        name: 'Alguien',
        initial: '?',
        color: '#8B909A',
        role: 'MEMBER',
      }
    );
  }

  async load(): Promise<Family[]> {
    const list = await firstValueFrom(this.http.get<Family[]>(this.config.api('/families/me')));
    this.families.set(list);
    return list;
  }

  /** Recarga solo la familia activa (p. ej. tras un evento `family.member`). */
  async reloadCurrent(): Promise<void> {
    const cur = this.current();
    if (!cur) {
      await this.load();
      return;
    }
    const fresh = await firstValueFrom(
      this.http.get<Family>(this.config.api(`/families/${cur.id}`)),
    );
    this.families.update((list) => (list ?? []).map((f) => (f.id === fresh.id ? fresh : f)));
  }

  async create(name: string, currency: string): Promise<Family> {
    const fam = await firstValueFrom(
      this.http.post<Family>(this.config.api('/families'), {
        name,
        currency,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      }),
    );
    await this.afterMembershipChange();
    return fam;
  }

  async update(
    changes: Partial<Pick<Family, 'name' | 'currency' | 'timezone' | 'membersCanInvite'>>,
  ) {
    const cur = this.current()!;
    const fam = await firstValueFrom(
      this.http.patch<Family>(this.config.api(`/families/${cur.id}`), changes),
    );
    this.families.update((list) => (list ?? []).map((f) => (f.id === fam.id ? fam : f)));
  }

  previewInvitation(codeOrToken: string): Promise<InvitationPreview> {
    return firstValueFrom(
      this.http.get<InvitationPreview>(
        this.config.api(`/invitations/${encodeURIComponent(codeOrToken)}`),
      ),
    );
  }

  async join(codeOrToken: string): Promise<JoinResult> {
    const res: HttpResponse<JoinResult> = await firstValueFrom(
      this.http.post<JoinResult>(
        this.config.api(`/invitations/${encodeURIComponent(codeOrToken)}/accept`),
        {},
        { observe: 'response' },
      ),
    );
    const body = res.body!;
    if (body.status === 'JOINED') {
      await this.afterMembershipChange();
    }
    return body;
  }

  invitations(): Promise<Invitation[]> {
    return firstValueFrom(
      this.http.get<Invitation[]>(this.config.api(`/families/${this.current()!.id}/invitations`)),
    );
  }

  createInvitation(body: {
    role?: FamilyRole;
    email?: string;
    expiresInDays?: number;
    requiresApproval?: boolean;
  }): Promise<Invitation> {
    return firstValueFrom(
      this.http.post<Invitation>(
        this.config.api(`/families/${this.current()!.id}/invitations`),
        body,
      ),
    );
  }

  revokeInvitation(id: string): Promise<unknown> {
    return firstValueFrom(
      this.http.delete(this.config.api(`/families/${this.current()!.id}/invitations/${id}`)),
    );
  }

  joinRequests(): Promise<JoinRequest[]> {
    return firstValueFrom(
      this.http.get<JoinRequest[]>(
        this.config.api(`/families/${this.current()!.id}/join-requests`),
      ),
    );
  }

  async decide(requestId: string, approve: boolean): Promise<void> {
    await firstValueFrom(
      this.http.post(
        this.config.api(
          `/families/${this.current()!.id}/join-requests/${requestId}/${approve ? 'approve' : 'reject'}`,
        ),
        {},
      ),
    );
    if (approve) await this.reloadCurrent();
  }

  async changeRole(userId: string, role: FamilyRole): Promise<void> {
    await firstValueFrom(
      this.http.patch(this.config.api(`/families/${this.current()!.id}/members/${userId}`), {
        role,
      }),
    );
    await this.reloadCurrent();
  }

  async removeMember(userId: string): Promise<void> {
    const leaving = userId === this.auth.profile()?.id;
    await firstValueFrom(
      this.http.delete(this.config.api(`/families/${this.current()!.id}/members/${userId}`)),
    );
    if (leaving) {
      await this.afterMembershipChange();
    } else {
      await this.reloadCurrent();
    }
  }

  clear(): void {
    this.families.set(null);
  }

  /** Tras crear, unirse o salir: renovar el token (claims `fid`/`role`) y recargar. */
  private async afterMembershipChange(): Promise<void> {
    await this.auth.refresh();
    await this.load();
  }
}

export function memberInfo(m: Member, index: number): MemberInfo {
  const name = m.nickname || m.displayName || m.email || 'Miembro';
  return {
    id: m.userId,
    name,
    initial: name.trim().charAt(0).toUpperCase() || '?',
    color: m.color || MEMBER_COLORS[index % MEMBER_COLORS.length],
    role: m.role,
  };
}

// Tipos de la API REST de Famy (ver backend/README.md). Fechas como cadenas ISO-8601.

export type FamilyRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'CHILD';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Profile {
  id: string;
  email: string;
  emailVerified: boolean;
  displayName: string;
  avatarUrl: string | null;
  color: string | null;
  birthday: string | null;
  timezone: string | null;
  locale: string | null;
  currency: string;
  hasPassword: boolean;
  linkedProviders: string[];
}

export interface Member {
  userId: string;
  displayName: string | null;
  email: string | null;
  avatarUrl: string | null;
  color: string | null;
  role: FamilyRole;
  nickname: string | null;
  joinedAt: string;
}

export interface Family {
  id: string;
  name: string;
  avatarUrl: string | null;
  currency: string;
  timezone: string;
  ownerId: string;
  membersCanInvite: boolean;
  myRole: FamilyRole;
  members: Member[];
  version: number;
}

export interface Invitation {
  id: string;
  code: string;
  role: FamilyRole;
  email?: string;
  maxUses?: number;
  uses: number;
  requiresApproval: boolean;
  expiresAt: string;
  createdBy: string;
  createdAt: string;
  token?: string;
  link?: string;
}

export interface InvitationPreview {
  familyId: string;
  familyName: string;
  familyAvatarUrl: string | null;
  memberCount: number;
  role: FamilyRole;
  requiresApproval: boolean;
  expiresAt: string;
}

export interface JoinResult {
  status: 'JOINED' | 'PENDING_APPROVAL';
  familyId: string;
  requestId: string | null;
}

export interface JoinRequest {
  id: string;
  userId: string;
  displayName: string | null;
  email: string | null;
  role: FamilyRole;
  status: string;
  createdAt: string;
}

export interface ShoppingList {
  id: string;
  name: string;
  icon: string | null;
  position: number;
  archived: boolean;
  archivedAt: string | null;
  totalItems: number;
  pendingItems: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ShoppingItem {
  id: string;
  listId: string;
  name: string;
  quantity: number | null;
  unit: string | null;
  category: string | null;
  note: string | null;
  checked: boolean;
  checkedAt: string | null;
  checkedBy: string | null;
  position: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ItemInput {
  name: string;
  quantity?: number | null;
  unit?: string | null;
  category?: string | null;
  note?: string | null;
}

export interface Suggestion {
  name: string;
  unit: string | null;
  category: string | null;
  uses: number;
}

export interface Chore {
  id: string;
  title: string;
  description: string | null;
  dueAt: string | null;
  rrule: string | null;
  points: number;
  assigneeId: string | null;
  rotation: boolean;
  rotationOrder: string[] | null;
  done: boolean;
  completedAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ChoreInput {
  title: string;
  description?: string | null;
  dueAt?: string | null;
  rrule?: string | null;
  points?: number;
  assigneeId?: string | null;
  rotation?: boolean;
  rotationOrder?: string[];
  version?: number;
}

export interface ChoreCompletion {
  id: string;
  choreId: string;
  completedBy: string;
  recordedBy: string;
  completedAt: string;
  occurrenceDate: string;
  points: number;
}

export interface Scoreboard {
  weekStart: string;
  weekEnd: string;
  members: { userId: string; displayName: string; points: number; completions: number }[];
}

export interface ChangeEvent {
  id: number;
  type: string;
  familyId: string;
  entity: string;
  entityId: string;
  op: 'C' | 'U' | 'D';
  version: number;
  actor: string | null;
  occurredAt: string;
  payload: unknown;
}

export interface ChangePage {
  changes: ChangeEvent[];
  cursor: number;
  hasMore: boolean;
}

/** Cuerpo de error RFC 9457 que devuelve la API. */
export interface Problem {
  status?: number;
  detail?: string;
  code?: string;
  errors?: Record<string, string>;
}

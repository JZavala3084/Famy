import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { normalizeCode } from '../features/onboarding/onboarding';
import { describeChange } from '../features/home/activity';
import { authInterceptor } from './auth.interceptor';
import { AuthService, decodeJwt } from './auth.service';
import { addDays, dayKey, weekStart } from './dates';
import { ChangeEvent } from './models';
import { upsert } from './versioned';

function jwt(claims: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'ES256' })}.${b64(claims)}.firma`;
}

describe('utilidades', () => {
  it('normaliza códigos de invitación', () => {
    expect(normalizeCode('fam-fm7k-2qab')).toBe('FM7K2QAB');
    expect(normalizeCode('FM7K 2QAB')).toBe('FM7K2QAB');
  });

  it('upsert respeta la versión', () => {
    const list = [{ id: 'a', version: 3, v: 'x' }];
    expect(upsert(list, { id: 'a', version: 2, v: 'viejo' })[0].v).toBe('x');
    expect(upsert(list, { id: 'a', version: 4, v: 'nuevo' })[0].v).toBe('nuevo');
    expect(upsert(list, { id: 'b', version: 0, v: 'otro' })).toHaveLength(2);
  });

  it('fechas en la zona de la familia', () => {
    expect(dayKey('2026-11-12T05:30:00Z', 'America/Mexico_City')).toBe('2026-11-11');
    expect(weekStart('2026-11-15')).toBe('2026-11-09'); // domingo → lunes anterior
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('decodifica los claims del JWT', () => {
    expect(decodeJwt(jwt({ sub: 'u1', fid: 'f1', exp: 10 }))).toEqual({
      sub: 'u1',
      fid: 'f1',
      exp: 10,
    });
    expect(decodeJwt('basura')).toBeNull();
  });

  it('describe la actividad', () => {
    const e = (p: Partial<ChangeEvent>) =>
      ({
        id: 1,
        type: '',
        familyId: 'f',
        entityId: 'x',
        version: 0,
        actor: 'u',
        occurredAt: '2026-11-11T10:00:00Z',
        ...p,
      }) as ChangeEvent;
    expect(
      describeChange(
        e({ entity: 'shopping.item', op: 'C', payload: { name: 'Huevos' } }),
        () => null,
      ),
    ).toBe('añadió «Huevos» a Compras');
    expect(
      describeChange(
        e({ entity: 'chore.completion', op: 'C', payload: { choreId: 'c1' } }),
        () => 'Lavar platos',
      ),
    ).toBe('completó «Lavar platos»');
  });
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => ctrl.verify());

  async function flushMicrotasks() {
    for (let i = 0; i < 5; i++) await Promise.resolve();
  }

  it('renueva con la cookie ante un 401 y reintenta una vez', async () => {
    const valid = jwt({ sub: 'u', exp: Math.floor(Date.now() / 1000) + 900 });
    const pending = firstValueFrom(http.get('/api/v1/me'));

    // Sin token: primero intenta renovar (la sesión puede venir de la cookie).
    await flushMicrotasks();
    const first = ctrl.expectOne('/api/v1/me');
    expect(first.request.headers.get('X-Famy-Client')).toBe('web');
    expect(first.request.withCredentials).toBe(true);
    expect(first.request.headers.has('Authorization')).toBe(false);
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    await flushMicrotasks();
    const refresh = ctrl.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({ accessToken: valid, tokenType: 'Bearer', expiresIn: 900 });

    await flushMicrotasks();
    const retry = ctrl.expectOne('/api/v1/me');
    expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${valid}`);
    retry.flush({ ok: true });
    expect(await pending).toEqual({ ok: true });
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('no toca peticiones fuera de la API', async () => {
    const pending = firstValueFrom(http.get('/config.json'));
    const req = ctrl.expectOne('/config.json');
    expect(req.request.headers.has('X-Famy-Client')).toBe(false);
    req.flush({});
    await pending;
  });
});

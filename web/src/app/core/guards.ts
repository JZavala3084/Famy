import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { FamilyService } from './family.service';

/** Requiere sesión iniciada. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Pantallas de acceso: si ya hay sesión, ir al inicio. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() ? inject(Router).createUrlTree(['/']) : true;

async function families() {
  const family = inject(FamilyService);
  return family.families() ?? (await family.load());
}

/** Requiere pertenecer a una familia; si no, va al onboarding. */
export const familyGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const list = await families().catch(() => []);
  return list.length ? true : router.createUrlTree(['/bienvenida']);
};

/** El onboarding solo tiene sentido sin familia. */
export const noFamilyGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const list = await families().catch(() => []);
  return list.length ? router.createUrlTree(['/']) : true;
};

/** Secciones solo para adultos (los hijos no ven los pagos). */
export const adultGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const family = inject(FamilyService);
  await families().catch(() => []);
  return family.isAdult() ? true : router.createUrlTree(['/']);
};

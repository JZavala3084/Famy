import { Routes } from '@angular/router';
import { authGuard, familyGuard, guestGuard, noFamilyGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Iniciar sesión · Famy',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'registro',
    canActivate: [guestGuard],
    title: 'Crear cuenta · Famy',
    loadComponent: () => import('./features/auth/register').then((m) => m.Register),
  },
  {
    path: 'olvide-contrasena',
    title: 'Recuperar contraseña · Famy',
    loadComponent: () => import('./features/auth/token-pages').then((m) => m.ForgotPassword),
  },
  // Rutas de los enlaces que envía el backend por correo (APP_BASE_URL + ruta).
  {
    path: 'verify-email',
    title: 'Confirmar correo · Famy',
    loadComponent: () => import('./features/auth/token-pages').then((m) => m.VerifyEmail),
  },
  {
    path: 'reset-password',
    title: 'Nueva contraseña · Famy',
    loadComponent: () => import('./features/auth/token-pages').then((m) => m.ResetPassword),
  },
  {
    path: 'join/:token',
    canActivate: [authGuard, noFamilyGuard],
    title: 'Unirme a una familia · Famy',
    loadComponent: () => import('./features/onboarding/onboarding').then((m) => m.Onboarding),
  },
  {
    path: 'bienvenida',
    canActivate: [authGuard, noFamilyGuard],
    title: 'Tu familia · Famy',
    loadComponent: () => import('./features/onboarding/onboarding').then((m) => m.Onboarding),
  },
  {
    path: '',
    canActivate: [authGuard, familyGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: '',
        title: 'Inicio · Famy',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'compras',
        title: 'Compras · Famy',
        loadComponent: () => import('./features/shopping/shopping').then((m) => m.Shopping),
      },
      {
        path: 'compras/:listId',
        title: 'Compras · Famy',
        loadComponent: () => import('./features/shopping/shopping').then((m) => m.Shopping),
      },
      {
        path: 'tareas',
        title: 'Tareas · Famy',
        loadComponent: () => import('./features/chores/chores').then((m) => m.Chores),
      },
      {
        path: 'familia',
        title: 'Familia · Famy',
        loadComponent: () => import('./features/family/family').then((m) => m.FamilyPage),
      },
      {
        path: 'configuracion',
        title: 'Configuración · Famy',
        loadComponent: () => import('./features/settings/settings').then((m) => m.Settings),
      },
      {
        path: 'pronto/:module',
        title: 'Próximamente · Famy',
        loadComponent: () => import('./features/soon/soon').then((m) => m.Soon),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

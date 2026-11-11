import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Rutas de /auth que no llevan access token ni disparan renovación. */
const PUBLIC_AUTH =
  /\/api\/v1\/auth\/(login|google|refresh|register|verify-email|resend-verification|logout|password\/(forgot|reset))$/;

/**
 * - Marca todas las peticiones a la API como cliente web (`X-Famy-Client: web`) y envía cookies.
 * - Añade `Authorization: Bearer`, renovando el token si está por vencer.
 * - Ante un 401 renueva una vez y reintenta; si no se puede, manda a iniciar sesión.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/v1/')) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const base = req.clone({ withCredentials: true, setHeaders: { 'X-Famy-Client': 'web' } });

  if (PUBLIC_AUTH.test(req.url)) {
    return next(base);
  }

  return from(auth.freshToken()).pipe(
    switchMap((token) => send(base, token, next)),
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      return from(auth.refresh()).pipe(
        switchMap((token) => {
          if (!token) {
            router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
            return throwError(() => err);
          }
          return send(base, token, next);
        }),
      );
    }),
  );
};

function send(
  req: HttpRequest<unknown>,
  token: string | null,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
}

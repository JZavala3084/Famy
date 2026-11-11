import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './models';

/** Extrae el cuerpo RFC 9457 de un error HTTP. */
export function problemOf(err: unknown): Problem {
  if (err instanceof HttpErrorResponse) {
    const body = err.error && typeof err.error === 'object' ? (err.error as Problem) : {};
    return { ...body, status: err.status };
  }
  return {};
}

/** Mensaje en español para mostrar al usuario. */
export function errorMessage(
  err: unknown,
  fallback = 'Ocurrió un error. Inténtalo de nuevo.',
): string {
  const p = problemOf(err);
  if (p.status === 0) {
    return 'No hay conexión con Famy. Revisa tu internet.';
  }
  if (p.status === 429) {
    return 'Demasiados intentos. Espera un minuto y vuelve a intentarlo.';
  }
  if (p.errors && Object.keys(p.errors).length) {
    return Object.entries(p.errors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join(' · ');
  }
  return p.detail || fallback;
}

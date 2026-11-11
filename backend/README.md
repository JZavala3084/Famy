# famy-api

Backend de Famy: Spring Boot 4.1 (Java 21) como monolito modular, PostgreSQL 16 + Flyway. Ver la arquitectura en [docs/04](../docs/04-arquitectura.md).

## Arrancar en local

```bash
# 1. PostgreSQL + Mailpit (bandeja de correo de desarrollo en http://localhost:8025)
docker compose -f deploy/docker-compose.dev.yml up -d

# 2. API en http://localhost:8080 (Swagger UI en /swagger-ui.html)
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

En `dev` la clave JWT se genera al arrancar (las sesiones se pierden al reiniciar) y los correos van a Mailpit.

## Pruebas

```bash
./gradlew test    # unitarias + integración con Testcontainers (requiere Docker)
```

`ModularityTest` verifica con Spring Modulith que ningún módulo use paquetes internos de otro ni haya ciclos.

## Módulos

| Módulo | Paquete | Contenido |
|--------|---------|-----------|
| shared | `com.famy.shared` | Errores RFC 9457, auditoría (`audit_log`), rate limiting (Bucket4j), UUID v7, configuración |
| identity | `com.famy.identity` | Registro, verificación de correo, login con correo y Google, JWT ES256 + refresh rotado, recuperación de contraseña, perfil |
| family | `com.famy.family` | Familias, miembros y roles, invitaciones (código, enlace/QR, correo), solicitudes de ingreso |

Cada módulo tiene `api` (controladores y DTO), `application` (casos de uso), `domain` (entidades) e `infrastructure` (repositorios y adaptadores). Lo que otros módulos pueden usar vive en el paquete raíz del módulo (p. ej. `identity.UserDirectory`).

## Endpoints (fase 2)

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/v1/auth/register` | Registro (siempre 202, no revela si el correo existe) |
| POST | `/api/v1/auth/verify-email` | Confirmar correo con el token del enlace |
| POST | `/api/v1/auth/resend-verification` | Reenviar verificación |
| POST | `/api/v1/auth/login` | Login con correo y contraseña |
| POST | `/api/v1/auth/google` | Login / registro con el `id_token` de Google |
| POST | `/api/v1/auth/refresh` | Rotar refresh token |
| POST | `/api/v1/auth/logout` · `/logout-all` | Cerrar sesión aquí / en todos los dispositivos |
| POST | `/api/v1/auth/password/forgot` · `/reset` · `/change` | Recuperar y cambiar contraseña |
| GET/PATCH | `/api/v1/me` | Perfil y configuración (moneda USD por defecto) |
| POST | `/api/v1/families` | Crear familia (requiere correo verificado) |
| GET | `/api/v1/families/me` · `/{id}` | Mis familias / detalle con miembros |
| PATCH | `/api/v1/families/{id}` | Editar (OWNER; ADMIN solo moneda) |
| POST/GET/DELETE | `/api/v1/families/{id}/invitations` | Crear, listar, revocar invitaciones |
| GET | `/api/v1/invitations/{codigoOToken}` | Vista previa |
| POST | `/api/v1/invitations/{codigoOToken}/accept` | Unirse (200 directo, 202 pendiente de aprobación) |
| GET/POST | `/api/v1/families/{id}/join-requests[/{rid}/approve\|reject]` | Solicitudes |
| PATCH/DELETE | `/api/v1/families/{id}/members/{userId}` | Cambiar rol / expulsar o salir |
| POST | `/api/v1/families/{id}/transfer-ownership` | Transferir propiedad |

## Sesiones

- **Access token**: JWT ES256 de 15 min con `sub`, `fid` (familia activa) y `role`. Tras crear o unirse a una familia, el cliente llama a `/auth/refresh` para recibir los claims nuevos. La autorización siempre se valida contra la base, no solo contra el token.
- **Refresh token**: opaco, 30 días, guardado como SHA-256, rotado en cada uso. Si llega uno ya rotado se revoca toda la cadena de ese dispositivo.
- **Web**: enviar `X-Famy-Client: web`. El refresh token va solo en la cookie `famy_rt` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`) y `/auth/refresh` exige esa cabecera. **Móvil**: el refresh token llega en el cuerpo y se guarda en Keystore / Keychain.

## Producción

Perfil `prod`: exige `JWT_PRIVATE_KEY_FILE` y SMTP. Generar la clave:

```bash
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out deploy/secrets/jwt-private.pem
```

Variables en [`deploy/.env.example`](../deploy/.env.example). La imagen se construye con el `Dockerfile` de esta carpeta (usuario no root, healthcheck en `/actuator/health/liveness`).

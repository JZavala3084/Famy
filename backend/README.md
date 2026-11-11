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
| realtime | `com.famy.realtime` | WebSocket STOMP en `/ws`, `change_log` y `GET /changes` para ponerse al día |
| shopping | `com.famy.shopping` | Listas de compras, ítems, marcar comprados, limpiar, reordenar, sugerencias |
| chore | `com.famy.chore` | Tareas del hogar con repetición (RRULE), rotación, historial y puntaje semanal |

Cada módulo tiene `api` (controladores y DTO), `application` (casos de uso), `domain` (entidades) e `infrastructure` (repositorios y adaptadores). Lo que otros módulos pueden usar vive en el paquete raíz del módulo (p. ej. `identity.UserDirectory`, `family.FamilyMembership`, `family.FamilyRole`). Lo transversal (`DomainChange`, `ChangePublisher`, `Recurrence`) vive en `shared`.

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

## Endpoints (fase 3)

Todos bajo `/api/v1/families/{familyId}` y solo para miembros activos (si no, 404).

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/changes?since={cursor}&limit=` | Cambios posteriores al cursor (máx. 500 por página, `hasMore` + `cursor`). 410 `resync_required` si el cursor ya se purgó |
| GET | `/changes/cursor` | Cursor actual, para guardarlo antes de la carga inicial |
| GET/POST | `/shopping/lists` | Listas con contadores (`?includeArchived=true`) / crear |
| PATCH/DELETE | `/shopping/lists/{listId}` | Renombrar, ícono, archivar / borrar (adultos) |
| POST | `/shopping/lists/reorder` | Orden nuevo de las listas (`{"ids": [...]}`) |
| GET/POST | `/shopping/lists/{listId}/items` | Ítems (pendientes primero, comprados al final) / agregar |
| PUT/DELETE | `/shopping/lists/{listId}/items/{itemId}` | Editar (reemplazo completo; `listId` en el cuerpo lo mueve de lista) / quitar |
| POST | `/shopping/lists/{listId}/items/{itemId}/check` · `/uncheck` | Marcar / desmarcar como comprado |
| POST | `/shopping/lists/{listId}/items/clear-checked` · `/reorder` | Limpiar comprados / reordenar |
| GET | `/shopping/suggestions?q=&listId=` | Ítems más frecuentes de la familia (excluye lo pendiente en `listId`) |
| GET/POST | `/chores` | Tareas por fecha límite (`?includeDone`, `?assigneeId`) / crear (adultos) |
| GET/PUT/DELETE | `/chores/{choreId}` | Detalle / editar / borrar (adultos) |
| POST | `/chores/{choreId}/complete` | Marcar como hecha (CHILD: solo las suyas; un adulto puede indicar `memberId`) |
| GET | `/chores/{choreId}/completions` | Historial |
| DELETE | `/chores/{choreId}/completions/{completionId}` | Deshacer la última marca |
| GET | `/chores/completions?date=` | Marcas de toda la familia en un día (hoy por defecto, zona de la familia). Las tareas que se repiten avanzan al marcarse; con esto el cliente sabe qué se hizo hoy |
| GET | `/chores/scoreboard?date=` | Puntos por miembro en la semana (lunes a domingo, zona de la familia) |

Las ediciones aceptan `version` opcional: si no coincide con la del servidor responde 409 `version_conflict`.

## Tiempo real

1. Al abrir la app: `GET /changes/cursor` → guardar el cursor, cargar los datos por REST.
2. Conectar STOMP a `wss://<host>/ws` con `Authorization: Bearer <access token>` en el frame **CONNECT** (el navegador no puede mandar cabeceras en el handshake). Heartbeat 20 s.
3. `SUBSCRIBE /topic/families/{familyId}` (solo miembros activos; cualquier otro destino o `SEND` cierra la conexión con un frame ERROR).
4. Cada mensaje es un evento:

```json
{ "id": 1234, "type": "shopping.item.created", "familyId": "…", "entity": "shopping.item", "entityId": "…",
  "op": "C", "version": 0, "actor": "…", "occurredAt": "2026-11-11T18:03:00Z", "payload": { …igual que la API REST… } }
```

   - `id` es el cursor: guardar el mayor visto. `version`: ignorar eventos con versión menor a la que ya se tiene.
   - En `op: "D"` el payload es `{ "id": … }`.
   - Entidades: `shopping.list`, `shopping.item`, `chore`, `chore.completion`, `family.member` (este último trae `{userId, role, active}`; recargar el detalle de la familia).
5. Al reconectar: `GET /changes?since=<cursor>` hasta que `hasMore` sea `false`. Con 410 `resync_required`, recargar todo.

El servidor cierra el WebSocket con código **4401** cuando vence el access token (reconectar con uno nuevo tras `/auth/refresh`) y con **4403** cuando el usuario sale o es expulsado de la familia.

El broker es el simple de Spring en memoria (una instancia), con `preservePublishOrder` para que cada sesión reciba los eventos en el orden en que se publicaron. Aun así el cliente deduplica por `id` en lugar de descartar todo lo menor al cursor. Los cambios se registran en `change_log` en la misma transacción que el dato y se envían después del commit; se conservan 30 días.

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

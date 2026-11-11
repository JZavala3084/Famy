# famy-web

Web de Famy en Angular 22 (standalone, signals, sin zone.js). Sigue el diseño aprobado ([docs/09](../docs/09-diseno-ui.md)): Plus Jakarta Sans, verde `#1E6B5E`, terracota `#C2562E`, menú lateral en escritorio y barra inferior en móvil.

## Arrancar en local

```bash
# 1. Backend (ver backend/README.md): PostgreSQL + Mailpit y la API en :8080
docker compose -f deploy/docker-compose.dev.yml up -d
(cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev')

# 2. Web en http://localhost:4200 (proxy de /api y /ws a :8080, ver proxy.conf.json)
cd web
npm ci
npm start
```

Los correos de verificación, recuperación e invitación llegan a Mailpit (http://localhost:8025) con enlaces a `http://localhost:4200/...`.

## Pruebas

```bash
npx ng test --watch=false          # Vitest
npx prettier --check "src/**/*.{ts,css,html}"
npx ng build                       # producción en dist/famy-web/browser
```

## Qué incluye (fases 4 y 5)

| Ruta | Pantalla |
|------|----------|
| `/login`, `/registro` | Correo y contraseña (medidor de fuerza con las reglas de 03 · Seguridad) y Google |
| `/verify-email`, `/olvide-contrasena`, `/reset-password` | Enlaces de los correos del backend |
| `/bienvenida`, `/join/:token` | Crear familia o unirse con código de 8 caracteres o enlace/QR; espera de aprobación |
| `/` | Panel familiar: miembros, indicadores, compras, tareas de hoy, actividad reciente en vivo |
| `/compras[/:listId]` | Listas, añadir rápido («2 kg tomate»), sugerencias, marcar comprado, editar, quitar comprados |
| `/tareas` | Hoy (progreso), Semana, Rotación, puntos de la semana; crear/editar con repetición y rotación |
| `/familia` | Miembros y roles, invitaciones con código, enlace y QR, solicitudes, datos de la familia |
| `/agenda` | Semana y mes con eventos, pagos, cumpleaños y tareas; filtro por persona; eventos que se repiten, recordatorios y cancelar un solo día (`?dia=` abre ese día) |
| `/pagos` | Solo adultos: total por pagar del mes, calendario, vencidos, marcar pagado con comprobante, deshacer; OWNER/ADMIN crean y editan (`?mes=YYYY-MM`) |
| `/cumpleanos` | Próximo festejo, todas las fechas (las de los miembros salen de su perfil) e ideas de regalo que la persona festejada no ve |
| Campana | Avisos en vivo (cola personal del WebSocket), panel con «marcar todo como leído» y aviso flotante |
| `/configuracion` | Perfil, color, moneda (USD por defecto), zona horaria, contraseña, notificaciones push (activar, probar, tipos y horas de silencio), sesiones |
| `/mas` | En móvil: las secciones que no caben en la barra inferior |
| `/pronto/:modulo` | Presupuesto, Recetas y Salud llegan en las fases 6 y 6b |

## Cómo funciona

- **Sesión** (`core/auth.service.ts`, `core/auth.interceptor.ts`): el access token (15 min) vive solo en memoria. Todas las peticiones llevan `X-Famy-Client: web`, así el refresh token viaja únicamente en la cookie `HttpOnly` `famy_rt`. Al recargar se recupera la sesión con `/auth/refresh`; el interceptor renueva el token antes de que venza y, ante un 401, renueva una vez y reintenta. `localStorage` solo guarda una marca (`famy_session`) para no pedir `/auth/refresh` a quien nunca inició sesión.
- **Tiempo real** (`core/realtime.service.ts`): guarda el cursor (`/changes/cursor`) antes de que las pantallas carguen por REST, conecta STOMP a `/ws` con el token en el frame CONNECT, se suscribe a `/topic/families/{id}` y, al (re)conectar, se pone al día con `/changes?since=`. Los eventos se deduplican por `id` y cada store ignora versiones menores a la que ya tiene. Cierre 4401 → renueva el token y reconecta; 4403 → vuelve a la bienvenida; 410 → recarga todo.
- **Push web** (`core/notifications.service.ts`, `public/sw.js`): registra el service worker, pide permiso solo al pulsar «Activar», se suscribe con la clave VAPID del servidor y la registra; al cerrar sesión borra la suscripción de este navegador. Nginx sirve `/sw.js` sin caché. En iPhone, push solo funciona con Famy añadida a la pantalla de inicio (`manifest.webmanifest`).
- **Estado** (`features/*/…store.ts`): servicios con signals que cargan por REST, aplican los eventos `shopping.*` y `chore*` y hacen actualizaciones optimistas al marcar.

## Configuración en tiempo de ejecución

La app lee `/config.json` al arrancar, así la misma imagen sirve para cualquier entorno:

| Variable del contenedor | Clave | Por defecto |
|-------------------------|-------|-------------|
| `FAMY_API_URL` | `apiUrl` | vacío = mismo origen (Caddy enruta `/api` y `/ws` a la API) |
| `GOOGLE_CLIENT_ID_WEB` | `googleClientId` | vacío = se oculta «Continuar con Google» |

En desarrollo se usa `public/config.json`. Para probar Google en local, pon ahí el Client ID web y añade `http://localhost:4200` como origen autorizado en Google Cloud.

## Imagen Docker

```bash
docker build -t famy-web web
docker run -p 8081:8080 -e GOOGLE_CLIENT_ID_WEB=... famy-web
```

Nginx sin privilegios en el puerto 8080, fallback del SPA, caché larga para los archivos con hash, `no-store` para `config.json`, CSP y cabeceras de seguridad (`docker/security-headers.conf`) y healthcheck en `/healthz`.

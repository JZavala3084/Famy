# 03 · Seguridad y requerimientos no funcionales

## Autenticación

### Correo y contraseña
- Contraseñas con **Argon2id** (`Argon2PasswordEncoder` de Spring Security: memoria 64 MB, 3 iteraciones, paralelismo 1). Se usa `DelegatingPasswordEncoder` para poder migrar el algoritmo en el futuro. Nunca se guarda ni se registra la contraseña en claro.
- Política: mínimo 10 caracteres, sin reglas de composición forzadas, rechazo de contraseñas filtradas conocidas (lista local de las más comunes; opcional consulta k-anonymity a HaveIBeenPwned).
- Verificación de correo obligatoria antes de crear o unirse a una familia.
- Recuperación con token aleatorio de 32 bytes, guardado como hash SHA-256, de un solo uso y expira en 30 min.

### Google
- OpenID Connect con Google. Web: Google Identity Services entrega un `id_token` al navegador (sin secreto de cliente en el backend). Móvil: Credential Manager (Android) / Google Sign-In (iOS) obtiene un `id_token` que el backend valida (firma, `aud`, `iss`, `exp`, `email_verified`).
- Si el correo ya existe y está verificado, se vincula la identidad de Google a esa cuenta (`user_identity`). Si existe pero nunca se verificó, Google la toma: se borra su contraseña y se cierran sus sesiones (evita que alguien registre antes el correo de otra persona).

### Sesiones y tokens
- **Access token JWT** firmado con ES256 (o RS256), vida de 15 min. Claims: `sub` (user id), `fid` (familia activa), `role`.
- **Refresh token** opaco, aleatorio, guardado como hash en `refresh_token`, vida de 30 días, **rotación** en cada uso y detección de reutilización (si se reutiliza uno ya rotado, se revoca toda la familia de tokens de ese dispositivo).
- Web: refresh token en cookie `HttpOnly; Secure; SameSite=Strict`; access token solo en memoria. Móvil: refresh token en Keystore (Android) / Keychain (iOS).
- Cerrar sesión revoca el refresh token; "cerrar en todos" revoca todos los del usuario.

## Autorización
- Cada petición a `/api/families/{familyId}/**` valida que el usuario sea miembro activo de esa familia (filtro + `@PreAuthorize` con un `FamilyPermissionEvaluator`).
- Permisos por rol definidos en [06 · Familias](06-familias.md#roles-y-permisos).
- Las consultas JPA siempre filtran por `family_id` (repositorios con el id de familia obligatorio) para evitar fugas entre familias (IDOR).
- Suscripciones WebSocket: solo a `/topic/families/{id}` si se es miembro; se valida en el `ChannelInterceptor` al conectar y al suscribir.

## Controles de seguridad (OWASP ASVS nivel 2 como guía)
| Control | Implementación |
|---------|----------------|
| Fuerza bruta | Rate limiting (Bucket4j) en login, registro, recuperación y unirse a familia; bloqueo progresivo por cuenta e IP |
| Enumeración de usuarios | Mismos mensajes y tiempos de respuesta en login y recuperación |
| HTTPS | TLS en el proxy inverso, HSTS |
| Cabeceras | CSP estricta, `X-Content-Type-Options`, `Referrer-Policy`, `Frame-Options: DENY` |
| CORS | Solo el origen de la web propia |
| CSRF | Endpoint de refresh con cookie protegido con `SameSite=Strict` + cabecera personalizada; el resto usa Bearer |
| Validación | Bean Validation en todos los DTO; tamaño máximo de petición y archivos |
| Archivos | Solo imágenes y PDF, verificación de tipo real, tamaño máximo 10 MB, nombres aleatorios, servidos con `Content-Disposition` |
| Secretos | Variables de entorno / Docker secrets, nunca en el repo; `.env.example` sin valores |
| Dependencias | Dependabot, OWASP Dependency-Check o Trivy en CI, escaneo de imágenes Docker |
| Auditoría | Tabla `audit_log` para login, cambios de rol, expulsiones, cambios de contraseña |
| Logs | Sin datos sensibles (contraseñas, tokens, montos completos con nombre) |
| Webhooks salientes | Firma HMAC-SHA256 con secreto por webhook, timestamp para evitar replays, bloqueo de IPs privadas (SSRF) salvo lista permitida del homelab |
| Datos de salud | Cifrado a nivel de columna con AES-GCM (clave fuera de la base), visibilidad por registro (solo yo, adultos, familia), cada lectura en `audit_log`, excluidos de logs y de payloads de push (la notificación dice «Hora de tu medicamento», sin el nombre en pantalla bloqueada si el usuario lo elige) |
| Datos en reposo | Disco del homelab cifrado; backups cifrados (ver [07](07-despliegue-homelab.md)) |

## Requerimientos no funcionales
| Tipo | Objetivo |
|------|----------|
| Latencia tiempo real | < 1 s entre un cambio y su aparición en otro dispositivo (LAN) |
| API | p95 < 200 ms en operaciones comunes |
| Disponibilidad | Best effort en homelab; reinicio automático de contenedores |
| Offline (móvil) | Lectura offline de listas y tareas; cola de cambios que se sincroniza al reconectar |
| Accesibilidad | WCAG 2.1 AA en web; tamaños de texto dinámicos en móvil |
| Idioma | Español primero; i18n preparada para inglés |
| Privacidad | Exportar y borrar la cuenta y los datos de la familia |
| Pruebas | Unitarias + integración con Testcontainers (PostgreSQL); cobertura mínima 70 % en dominio |

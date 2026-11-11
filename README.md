# Famy

Aplicación web y móvil (Android e iOS) para organizar la vida de una familia en un solo lugar: lista de compras, presupuesto, recetas, horarios, tareas del hogar, calendario de pagos, cumpleaños y más. Todos los miembros ven los cambios al instante.

> Estado: **Especificación y diseño aprobados (2026-11-11).** Siguiente: Fase 2 – Fundaciones del backend. Ver [08 · Roadmap](docs/08-roadmap.md).

## Propuesta de valor

- Una sola app para toda la familia, con los cambios de cada miembro reflejados en tiempo real en los demás dispositivos.
- Registro e inicio de sesión con Google o con correo y contraseña.
- Crear una familia o unirse a una existente con un código o enlace de invitación.
- Recordatorios de pagos, cumpleaños y tareas por notificación push.
- Autoalojada: se despliega inicialmente en el homelab de Jose con Docker.

## Documentación

| # | Documento | Contenido |
|---|-----------|-----------|
| 01 | [Visión y alcance](docs/01-vision-y-alcance.md) | Problema, usuarios, alcance del MVP y fuera de alcance |
| 02 | [Requerimientos funcionales](docs/02-requerimientos-funcionales.md) | Módulos y reglas de negocio |
| 03 | [Seguridad y requerimientos no funcionales](docs/03-seguridad-y-no-funcionales.md) | Auth (Google + correo), hash de contraseñas, JWT, OWASP, rendimiento |
| 04 | [Arquitectura](docs/04-arquitectura.md) | Stack, componentes, tiempo real, estructura del repo |
| 05 | [Modelo de datos](docs/05-modelo-de-datos.md) | Entidades, relaciones y diagrama ER |
| 06 | [Familias: crear y unirse](docs/06-familias.md) | Flujo de alta, invitaciones, roles y permisos |
| 07 | [Despliegue en homelab](docs/07-despliegue-homelab.md) | Docker Compose, proxy inverso, backups, CI/CD |
| 08 | [Roadmap](docs/08-roadmap.md) | Fases, hitos y decisiones pendientes |
| 09 | [Diseño UI y prototipo](docs/09-diseno-ui.md) | Sistema visual, pantallas web y móvil, prototipo navegable |

## Stack

| Capa | Tecnología |
|------|------------|
| Backend | Java 21 + Spring Boot (Web, Security, Data JPA, WebSocket, Validation) |
| Base de datos | PostgreSQL 16 + Flyway |
| Web | Angular (standalone components, Angular Material o Tailwind según diseño) |
| Móvil | Kotlin Multiplatform + Compose Multiplatform (Android e iOS) |
| Tiempo real | WebSocket (STOMP) + notificaciones push (FCM / APNs) |
| Despliegue | Docker Compose en homelab, detrás de proxy inverso con HTTPS |

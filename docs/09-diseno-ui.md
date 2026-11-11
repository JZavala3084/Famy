# 09 · Diseño UI y prototipo

> Estado: **pendiente de aprobación de Jose.** Nada se construye hasta aprobar este diseño.

- **Lienzo de diseño y prototipo navegable:** https://claude.ai/artifact/UMXgzhpK1zMaVistkGoY1W (privado; se exporta a Canva, PDF o PNG desde *Compartir › Exportar*).
- **Fuentes del prototipo:** [`design/prototipo/`](../design/prototipo/) (un archivo `.dc.html` por pantalla y `canvas.json` con la distribución).

## Sistema visual

| Token | Valor | Uso |
|-------|-------|-----|
| Tipografía | Plus Jakarta Sans 400–800 | Toda la interfaz (web y móvil) |
| Fondo | `#F6F5F1` | Fondo de pantalla |
| Superficie | `#FFFFFF` + borde `#E4E2DC` | Tarjetas, listas, inputs |
| Texto | `#1B1D22` | Texto principal |
| Texto secundario | `#5E6470` | Metadatos, ayudas (contraste ≥ 4.5:1) |
| Primario | `#1E6B5E` · suave `#E3F0EC` | Botones, selección, progreso |
| Acento | `#C2562E` · suave `#FBE9E0` | Pagos próximos, alertas, excesos de presupuesto |
| Colores de miembro | `#1E6B5E` `#C2562E` `#6B4FA0` `#2F5DA8` `#8A6A12` | Avatares y eventos por persona |
| Radios | 12 px (inputs) · 14–18 px (tarjetas) · 22 px (destacados) | |
| Toques | mínimo 44 px | Accesibilidad móvil |

Indicador **«En vivo»** (punto verde): representa los cambios que llegan en tiempo real por WebSocket/webhooks desde Spring Boot.

## Pantallas

### Acceso y familia (móvil)
1. **Iniciar sesión** – correo + contraseña, «¿La olvidaste?», continuar con Google, nota de seguridad (Argon2, conexión cifrada).
2. **Registro** – nombre, correo, contraseña con medidor de fuerza y reglas (8+ caracteres, mayúsculas/minúsculas, número), aceptación de términos, registro con Google.
3. **Crear o unirse a familia** – pestañas: *Crear* (nombre y color, el creador es administrador y recibe código/QR) y *Unirme* (código de 6 caracteres o QR; el administrador aprueba).

### Módulos (móvil, barra inferior: Inicio · Compras · Tareas · Finanzas · Agenda)
4. **Inicio** – saludo, miembros, actividad en vivo, resumen (compras, tareas, presupuesto, próximo pago), agenda de hoy y accesos a los 8 módulos.
5. **Compras** – listas múltiples, añadir artículo, marcar como comprado (interactivo), quién lo añadió, categoría y cantidad.
6. **Presupuesto** – gasto del mes vs. límite, barras por categoría (terracota si se excede), últimos movimientos.
7. **Recetas** – búsqueda, filtros, receta del día con botón «Añadir ingredientes faltantes a Compras», cuadrícula de recetas.
8. **Horarios** – selector de semana (interactivo), filtros por miembro, línea de tiempo del día.
9. **Tareas del hogar** – Hoy/Semana/Rotación, progreso circular, racha, tareas asignadas (interactivo).
10. **Calendario de pagos** – mes con puntos (pagado/pendiente), próximos pagos con responsable y monto.
11. **Cumpleaños** – próximo cumpleaños destacado (días, ideas de regalo, presupuesto), recordatorios 7 y 1 día antes, lista anual.

### Salud y medicamentos (móvil)
13. **Salud** – selector de miembro, condiciones y alergias (con nivel de visibilidad), tomas del día con botón «Tomar» (interactivo), estado atrasada/pendiente/tomada y aviso de pocas existencias.
14. **Agregar medicamento** – para quién, nombre, dosis, cantidad, existencias, frecuencia (todos los días, cada X horas, días específicos, si es necesario), horarios, fechas, interruptores de recordatorio (aviso a la hora, repetir, avisar a un adulto, reposición) y quién puede verlo.
15. **Recordatorio de toma** – cómo llega la notificación push y la pantalla de alarma con «Ya la tomé», «Posponer 10 min» y «Omitir toma».

En Inicio, «Salud» reemplaza a «Familia» en los accesos (la familia se gestiona desde el perfil) y aparece un aviso de toma pendiente. En el panel web se añade «Salud» al menú y un panel «Tomas de hoy».

### Configuración (móvil)
16. **Configuración** – perfil, moneda (USD por defecto, con vista previa del formato), idioma, zona horaria, notificaciones (medicamentos, pagos, actividad, horario de silencio), familia, seguridad y cerrar sesión. Se abre tocando los avatares del Inicio.

En *Crear familia* se añadió la moneda del presupuesto familiar, y el código de invitación ahora tiene 8 caracteres (igual que en 06 · Familias).

### Web (Angular)
12. **Panel familiar** – menú lateral con todos los módulos (se apila en móvil), buscador, invitar, 4 indicadores y paneles de compras, agenda, presupuesto, pagos, tareas y actividad reciente.

### Componentes
- **Barra inferior** (`TabBar`) reutilizable con estado activo.

## Supuestos
- Montos de ejemplo en USD (`$`); la moneda la elige cada usuario en Configuración. Fechas en español.
- Nombres y montos son datos de ejemplo.
- Fotos de recetas son marcadores de posición.

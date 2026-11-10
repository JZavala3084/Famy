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

### Web (Angular)
12. **Panel familiar** – menú lateral con todos los módulos (se apila en móvil), buscador, invitar, 4 indicadores y paneles de compras, agenda, presupuesto, pagos, tareas y actividad reciente.

### Componentes
- **Barra inferior** (`TabBar`) reutilizable con estado activo.

## Supuestos
- Moneda en pesos (`$`) y formato de fecha en español.
- Nombres y montos son datos de ejemplo.
- Fotos de recetas son marcadores de posición.

# 02 · Requerimientos funcionales

Todos los módulos viven dentro de una **familia**. Cada dato pertenece a una familia (`family_id`) y solo sus miembros pueden verlo. Cada creación, edición o borrado se emite en tiempo real a los demás miembros (ver [04 · Arquitectura](04-arquitectura.md#tiempo-real)).

## Módulos

### M1. Cuenta y autenticación
- RF-1.1 Registro con correo, nombre y contraseña; verificación del correo con enlace.
- RF-1.2 Registro / login con Google (OAuth 2.0 / OpenID Connect).
- RF-1.3 Vincular Google a una cuenta creada con correo (mismo correo verificado).
- RF-1.4 Recuperar contraseña por correo (token de un solo uso, 30 min).
- RF-1.5 Perfil: nombre, foto, color, fecha de cumpleaños, zona horaria.
- RF-1.6 Cerrar sesión en este dispositivo o en todos.

### M2. Familia
- RF-2.1 Crear familia (nombre, foto, moneda, zona horaria). El creador queda como `OWNER`.
- RF-2.2 Unirse con código de 8 caracteres o enlace de invitación.
- RF-2.3 Roles: `OWNER`, `ADMIN`, `MEMBER`, `CHILD`. Detalle en [06 · Familias](06-familias.md).
- RF-2.4 Salir de la familia, expulsar miembros, transferir propiedad.

### M3. Lista de compras
- RF-3.1 Varias listas por familia (Supermercado, Farmacia...).
- RF-3.2 Ítem: nombre, cantidad, unidad, categoría (pasillo), nota, quién lo agregó.
- RF-3.3 Marcar como comprado; los comprados bajan al final y se pueden limpiar.
- RF-3.4 Sugerencias con los ítems más frecuentes.
- RF-3.5 Opcional: al cerrar una compra, registrar el total como gasto en Presupuesto.

### M4. Presupuesto
- RF-4.1 Presupuesto mensual con categorías y monto límite por categoría.
- RF-4.2 Registrar gastos e ingresos (monto, categoría, fecha, quién, nota).
- RF-4.3 Resumen: gastado vs. presupuesto, por categoría y por miembro.
- RF-4.4 Alerta al llegar al 80 % y al 100 % de una categoría.
- RF-4.5 Copiar el presupuesto del mes anterior.

### M5. Recetas
- RF-5.1 Receta: título, foto, porciones, tiempo, ingredientes, pasos, etiquetas.
- RF-5.2 Enviar ingredientes a una lista de compras (ajustando porciones).
- RF-5.3 Planificador semanal de comidas (opcional en MVP, deseable).

### M6. Horarios y calendario
- RF-6.1 Eventos: título, inicio, fin, todo el día, ubicación, participantes, color.
- RF-6.2 Repetición (diaria, semanal, mensual, anual) con formato RRULE.
- RF-6.3 Vista día / semana / mes, filtrable por miembro.
- RF-6.4 El calendario muestra en una sola vista eventos, cumpleaños, pagos y tareas con fecha.
- RF-6.5 Recordatorios configurables por evento.

### M7. Tareas del hogar
- RF-7.1 Tarea: título, descripción, responsable(s), fecha límite, repetición, puntos.
- RF-7.2 Rotación automática entre miembros para tareas repetitivas.
- RF-7.3 Marcar como hecha; historial de quién la hizo.
- RF-7.4 Puntaje semanal por miembro (gamificación ligera para hijos).

### M8. Calendario de pagos
- RF-8.1 Pago: nombre (luz, agua, internet, colegio), monto, moneda, fecha de vencimiento, repetición, responsable, categoría de presupuesto.
- RF-8.2 Estados: pendiente, pagado, vencido. Marcar pagado registra el gasto en Presupuesto.
- RF-8.3 Recordatorios N días antes y el día del vencimiento.
- RF-8.4 Adjuntar comprobante (imagen / PDF).

### M9. Cumpleaños y fechas especiales
- RF-9.1 Cumpleaños de miembros (desde su perfil) y de personas externas (abuelos, amigos).
- RF-9.2 Aniversarios y otras fechas anuales.
- RF-9.3 Recordatorio 7 días y 1 día antes, y el mismo día.
- RF-9.4 Ideas de regalo por persona.

### M10. Inicio (dashboard)
- RF-10.1 Resumen del día: eventos, tareas pendientes, pagos próximos, cumpleaños cercanos, estado del presupuesto.
- RF-10.2 Actividad reciente de la familia ("Ana agregó leche a la lista").

### M11. Notificaciones
- RF-11.1 Push en móvil (FCM para Android, APNs vía FCM para iOS) y notificaciones web.
- RF-11.2 Preferencias por tipo de notificación y horario de silencio.
- RF-11.3 Centro de notificaciones dentro de la app.

### M12. Webhooks salientes (post-MVP)
- RF-12.1 La familia puede registrar URLs que reciben eventos (`shopping.item.created`, `payment.due`...) firmados con HMAC, para integrarse con Home Assistant, n8n, Telegram, etc.

### M13. Salud y medicamentos
- RF-13.1 Cada miembro registra sus condiciones de salud y alergias (nombre, tipo, notas). Un adulto administrador puede gestionar los datos de los menores.
- RF-13.2 Medicamentos por persona: nombre, dosis, cantidad por toma, instrucciones (en ayunas, con comida), fecha de inicio y fin o tratamiento continuo.
- RF-13.3 Frecuencia: todos los días a horas fijas, cada X horas, días específicos de la semana o solo si es necesario.
- RF-13.4 Notificación push (y alarma local en el móvil) a la hora de cada toma, con acciones «Tomada», «Posponer 10 min» y «Omitir».
- RF-13.5 Si la toma no se confirma, se repite el aviso (configurable) y, opcionalmente, se avisa a un adulto de la familia tras 30 min.
- RF-13.6 Historial de tomas (tomada, omitida, atrasada) por día.
- RF-13.7 Control de existencias: al llegar al mínimo configurado se avisa y se agrega el medicamento a la lista de Compras › Farmacia.
- RF-13.8 Visibilidad por registro: solo yo, adultos de la familia o toda la familia.
- RF-13.9 Los datos de salud se cifran en reposo a nivel de columna y cada acceso queda en `audit_log`.

## Reglas generales
- Toda entidad registra `created_by`, `created_at`, `updated_at`.
- Borrado lógico (`deleted_at`) en datos de usuario para poder deshacer y sincronizar.
- Fechas en UTC en la base; se muestran en la zona horaria de la familia.
- Montos en `NUMERIC(14,2)` con moneda ISO 4217.

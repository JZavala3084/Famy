# 01 · Visión y alcance

## Problema

La organización de una familia está repartida entre chats de WhatsApp, notas sueltas, hojas de cálculo y la memoria de una o dos personas. Se olvidan pagos, cumpleaños y tomas de medicamentos, se compra dos veces lo mismo, las tareas del hogar caen siempre en las mismas personas y nadie sabe cuánto queda del presupuesto del mes.

## Visión

Famy es el "centro de mando" de la familia: un espacio compartido donde cada miembro ve y actualiza compras, presupuesto, recetas, horarios, tareas, pagos, cumpleaños y salud, y donde cualquier cambio aparece al instante en los dispositivos de los demás.

## Usuarios

| Perfil | Necesidad principal |
|--------|---------------------|
| Administrador de la familia (padre/madre) | Crear la familia, invitar, controlar presupuesto y pagos |
| Miembro adulto | Agregar compras, marcar tareas, ver horarios y pagos |
| Miembro que toma medicamentos (p. ej. abuelos) | Recordatorio a la hora de cada toma y aviso a un adulto si no la confirma |
| Miembro joven / hijo | Ver y completar sus tareas, ver horarios, agregar a la lista de compras |

## Alcance del MVP

1. Registro e inicio de sesión con Google o correo y contraseña.
2. Crear una familia o unirse a una con código / enlace de invitación.
3. Lista de compras compartida en tiempo real.
4. Tareas del hogar con responsable, fecha y repetición.
5. Calendario familiar con horarios (eventos), cumpleaños y pagos.
6. Calendario de pagos con recordatorios.
7. Presupuesto mensual por categorías con registro de gastos.
8. Recetas con ingredientes que se pueden enviar a la lista de compras.
9. Salud y medicamentos: condiciones y alergias por miembro, medicamentos con horarios, recordatorios push de cada toma, historial y control de existencias.
10. Notificaciones push y actualizaciones en tiempo real.
11. Web (Angular) y app Android + iOS (Kotlin Multiplatform).

## Fuera de alcance (por ahora)

- Integración con bancos o pagos reales.
- Pertenecer a varias familias al mismo tiempo (el modelo de datos lo soporta, la UI no en el MVP).
- Chat interno.
- Historia clínica, diagnósticos o integración con médicos y farmacias (Salud es solo organización familiar, no consejo médico).
- Ubicación en tiempo real de los miembros.
- Publicación en tiendas (se distribuye primero como APK / TestFlight).

## Métricas de éxito

- La familia de Jose la usa a diario durante un mes sin volver a las notas sueltas.
- Un cambio hecho en un dispositivo aparece en otro en menos de 1 segundo en la red local.
- Ningún pago ni cumpleaños se olvida en ese mes.
- Más del 95 % de las tomas de medicamentos programadas se confirman.

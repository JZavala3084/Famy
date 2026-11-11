/**
 * Módulo notification: centro de notificaciones (RF-11.3), preferencias y horario de silencio (RF-11.2), push web
 * con VAPID (RF-11.1) y el job de recordatorios programados. Los módulos con fechas (calendar, payment, celebration)
 * exponen un {@link com.famy.notification.ReminderSource}; este módulo revisa cada minuto qué recordatorios tocan,
 * los registra una sola vez en {@code reminder_log} y los entrega.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package com.famy.notification;

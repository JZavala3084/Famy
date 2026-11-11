package com.famy.notification;

import java.time.Instant;
import java.util.List;

/** Fuente de recordatorios programados (eventos, pagos, cumpleaños). */
public interface ReminderSource {

    /** Recordatorios cuyo momento de envío cae en {@code (from, to]}. */
    List<Reminder> remindersBetween(Instant from, Instant to);
}

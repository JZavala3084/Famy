package com.famy.notification;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Un recordatorio que toca enviar.
 *
 * @param key        clave estable y única (tipo, id, ocurrencia y anticipación) para no enviarlo dos veces
 * @param recipients usuarios que lo reciben
 * @param link       ruta de la web a la que lleva (p. ej. {@code /agenda?dia=2026-11-20})
 */
public record Reminder(String key, UUID familyId, Set<UUID> recipients, NotificationType type, String title,
        String body, String link, Instant fireAt) {

    /** Hora local (zona de la familia) de los avisos por día: pagos y cumpleaños. */
    public static final LocalTime DAILY_HOUR = LocalTime.of(9, 0);

    public Reminder {
        if (key.length() > 200) {
            throw new IllegalArgumentException("Clave de recordatorio demasiado larga");
        }
        recipients = Set.copyOf(recipients);
    }
}

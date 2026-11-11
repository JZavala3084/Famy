package com.famy.notification.application;

import com.famy.notification.Reminder;
import com.famy.notification.ReminderSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recordatorios programados. Cada minuto pregunta a cada {@link ReminderSource} qué avisos caen entre la última
 * revisión y ahora, registra cada uno una sola vez en {@code reminder_log} y crea las notificaciones.
 * <p>En lugar de precalcular filas de {@code scheduled_reminder} (frágil con repeticiones y ediciones), se calculan al
 * vuelo desde las reglas; la clave estable de cada aviso evita duplicados. Si la API estuvo apagada, al volver se
 * envían los avisos de las últimas 6 horas y se descartan los más viejos.
 */
@Component
public class ReminderJob {

    static final Duration MAX_CATCH_UP = Duration.ofHours(6);
    static final Duration LOG_RETENTION = Duration.ofDays(120);

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final List<ReminderSource> sources;
    private final NotificationService notifications;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public ReminderJob(List<ReminderSource> sources, NotificationService notifications, JdbcClient jdbc,
            TransactionTemplate tx) {
        this.sources = sources;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${famy.reminders.interval:60s}", initialDelayString = "${famy.reminders.initial-delay:20s}")
    public void tick() {
        try {
            runUntil(Instant.now());
        } catch (RuntimeException e) {
            log.error("Falló la revisión de recordatorios", e);
        }
    }

    /** Envía los recordatorios pendientes hasta {@code now}; devuelve cuántos se enviaron. */
    public int runUntil(Instant now) {
        Instant checked = jdbc.sql("SELECT checked_up_to FROM reminder_checkpoint WHERE id = 1")
                .query(Timestamp.class).single().toInstant();
        Instant from = checked.isBefore(now.minus(MAX_CATCH_UP)) ? now.minus(MAX_CATCH_UP) : checked;
        if (!now.isAfter(from)) {
            return 0;
        }
        // Las fuentes se consultan fuera de la transacción de escritura: si una falla, las demás siguen.
        List<Reminder> due = new ArrayList<>();
        for (ReminderSource source : sources) {
            try {
                due.addAll(source.remindersBetween(from, now));
            } catch (RuntimeException e) {
                log.error("Falló la fuente de recordatorios {}", source.getClass().getSimpleName(), e);
            }
        }
        Integer sent = tx.execute(status -> {
            Optional<Timestamp> locked = jdbc.sql(
                    "SELECT checked_up_to FROM reminder_checkpoint WHERE id = 1 FOR UPDATE SKIP LOCKED")
                    .query(Timestamp.class).optional();
            if (locked.isEmpty() || !locked.get().toInstant().equals(checked)) {
                return 0; // otra instancia avanzó mientras tanto
            }
            int count = 0;
            for (Reminder r : due) {
                int inserted = jdbc.sql("""
                        INSERT INTO reminder_log (reminder_key, family_id, fire_at) VALUES (:key, :familyId, :fireAt)
                        ON CONFLICT DO NOTHING""")
                        .param("key", r.key()).param("familyId", r.familyId())
                        .param("fireAt", Timestamp.from(r.fireAt()))
                        .update();
                if (inserted == 1 && !r.recipients().isEmpty()) {
                    notifications.notify(r.recipients(), r.familyId(), r.type(), r.title(), r.body(), r.link());
                    count++;
                }
            }
            jdbc.sql("UPDATE reminder_checkpoint SET checked_up_to = :now WHERE id = 1")
                    .param("now", Timestamp.from(now)).update();
            return count;
        });
        if (sent != null && sent > 0) {
            log.info("{} recordatorios enviados", sent);
        }
        return sent == null ? 0 : sent;
    }

    @Scheduled(cron = "0 23 4 * * *")
    public void purge() {
        jdbc.sql("DELETE FROM reminder_log WHERE sent_at < :before")
                .param("before", Timestamp.from(Instant.now().minus(LOG_RETENTION))).update();
    }
}

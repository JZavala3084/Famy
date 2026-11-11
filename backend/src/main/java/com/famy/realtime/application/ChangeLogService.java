package com.famy.realtime.application;

import com.famy.shared.error.ApiException;
import com.famy.shared.events.DomainChange;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Registro de cambios ({@code change_log}) y difusión por WebSocket.
 * <p>El INSERT ocurre en la misma transacción que el cambio, precedido de un advisory lock por familia: así los
 * cursores de una familia quedan en orden de commit y un cliente que pide {@code since=N} nunca se salta un cambio que
 * se confirmó tarde. El envío por STOMP se hace después del commit.
 */
@Service
public class ChangeLogService {

    public static final Duration RETENTION = Duration.ofDays(30);
    public static final int MAX_PAGE = 500;

    private static final Logger log = LoggerFactory.getLogger(ChangeLogService.class);

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate broker;

    public ChangeLogService(JdbcClient jdbc, ObjectMapper mapper, SimpMessagingTemplate broker) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.broker = broker;
    }

    public static String topic(UUID familyId) {
        return "/topic/families/" + familyId;
    }

    /** Cambios que solo ven los adultos (pagos...): los hijos no pueden suscribirse a este destino. */
    public static String adultsTopic(UUID familyId) {
        return topic(familyId) + "/adults";
    }

    public record ChangePage(List<ChangeEvent> changes, long cursor, boolean hasMore) {
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DomainChange change) {
        String payload = mapper.writeValueAsString(change.payload());
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:familyId AS text), 0))")
                .param("familyId", change.familyId())
                .query((rs, n) -> 1).list();
        Instant now = Instant.now();
        long id = jdbc.sql("""
                INSERT INTO change_log (family_id, entity, entity_id, op, version, actor_id, occurred_at, payload,
                                        audience)
                VALUES (:familyId, :entity, :entityId, :op, :version, :actor, :occurredAt, CAST(:payload AS jsonb),
                        :audience)
                RETURNING id
                """)
                .param("familyId", change.familyId())
                .param("entity", change.entity())
                .param("entityId", change.entityId())
                .param("op", change.op().name())
                .param("version", change.version())
                .param("actor", change.actorId())
                .param("occurredAt", Timestamp.from(now))
                .param("payload", payload)
                .param("audience", change.audience().name())
                .query(Long.class).single();
        String destination = change.audience() == DomainChange.Audience.ADULTS
                ? adultsTopic(change.familyId()) : topic(change.familyId());
        ChangeEvent event = new ChangeEvent(id, change.type(), change.familyId(), change.entity(), change.entityId(),
                change.op().name(), change.version(), change.actorId(), now, mapper.readTree(payload));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    broker.convertAndSend(destination, event);
                } catch (RuntimeException e) {
                    // El cliente lo recupera con GET /changes al reconectar.
                    log.warn("No se pudo difundir el cambio {}: {}", event.id(), e.getMessage());
                }
            }
        });
    }

    /**
     * Cambios con cursor mayor a {@code since}; 410 si ese cursor ya se purgó (el cliente debe recargar todo).
     *
     * @param includeAdultsOnly si se incluyen los cambios solo para adultos (falso para un CHILD)
     */
    @Transactional(readOnly = true)
    public ChangePage since(UUID familyId, long since, int limit, boolean includeAdultsOnly) {
        int size = Math.clamp(limit, 1, MAX_PAGE);
        long purgedUpTo = jdbc.sql("SELECT purged_up_to FROM change_log_watermark WHERE id = 1")
                .query(Long.class).single();
        if (since < purgedUpTo) {
            throw new ApiException(HttpStatus.GONE, "resync_required",
                    "Pasó demasiado tiempo sin sincronizar. Vuelve a cargar los datos.");
        }
        List<ChangeEvent> rows = jdbc.sql("""
                SELECT id, family_id, entity, entity_id, op, version, actor_id, occurred_at, payload::text AS payload
                FROM change_log WHERE family_id = :familyId AND id > :since
                  AND (audience = 'FAMILY' OR :adults)
                ORDER BY id LIMIT :limit
                """)
                .param("familyId", familyId)
                .param("adults", includeAdultsOnly)
                .param("since", since)
                .param("limit", size + 1)
                .query((rs, n) -> {
                    String entity = rs.getString("entity");
                    DomainChange.Op op = DomainChange.Op.valueOf(rs.getString("op"));
                    return new ChangeEvent(rs.getLong("id"), entity + "." + op.verb(),
                            rs.getObject("family_id", UUID.class), entity, rs.getObject("entity_id", UUID.class),
                            op.name(), rs.getLong("version"), rs.getObject("actor_id", UUID.class),
                            rs.getTimestamp("occurred_at").toInstant(), mapper.readTree(rs.getString("payload")));
                })
                .list();
        boolean hasMore = rows.size() > size;
        List<ChangeEvent> page = hasMore ? rows.subList(0, size) : rows;
        long cursor = page.isEmpty() ? Math.max(since, currentCursor(familyId)) : page.getLast().id();
        return new ChangePage(page, cursor, hasMore);
    }

    /** Cursor actual de la familia: el cliente lo guarda antes de la carga inicial. */
    @Transactional(readOnly = true)
    public long currentCursor(UUID familyId) {
        long max = jdbc.sql("SELECT COALESCE(max(id), -1) FROM change_log WHERE family_id = :familyId")
                .param("familyId", familyId)
                .query(Long.class).single();
        if (max >= 0) {
            return max;
        }
        return jdbc.sql("SELECT purged_up_to FROM change_log_watermark WHERE id = 1").query(Long.class).single();
    }

    /** Purga diaria: se conservan 30 días de cambios. */
    @Scheduled(cron = "0 41 3 * * *")
    @Transactional
    public void purge() {
        long maxPurged = jdbc.sql("""
                WITH deleted AS (DELETE FROM change_log WHERE occurred_at < :limit RETURNING id)
                SELECT COALESCE(max(id), 0) FROM deleted
                """)
                .param("limit", Timestamp.from(Instant.now().minus(RETENTION)))
                .query(Long.class).single();
        if (maxPurged > 0) {
            jdbc.sql("UPDATE change_log_watermark SET purged_up_to = GREATEST(purged_up_to, :max) WHERE id = 1")
                    .param("max", maxPurged)
                    .update();
            log.info("change_log purgado hasta el cursor {}", maxPurged);
        }
    }
}

package com.famy.shared.events;

import com.famy.shared.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica {@link DomainChange} con el usuario actual como actor. Debe llamarse dentro de una transacción. Por defecto
 * los ve toda la familia; {@link #adults()} devuelve un publicador cuyos cambios solo reciben los adultos.
 */
@Component
public class ChangePublisher {

    private final ApplicationEventPublisher events;
    private final DomainChange.Audience audience;

    @Autowired
    public ChangePublisher(ApplicationEventPublisher events) {
        this(events, DomainChange.Audience.FAMILY);
    }

    private ChangePublisher(ApplicationEventPublisher events, DomainChange.Audience audience) {
        this.events = events;
        this.audience = audience;
    }

    /** Publicador para datos que los hijos no ven (pagos, presupuesto). */
    public ChangePublisher adults() {
        return new ChangePublisher(events, DomainChange.Audience.ADULTS);
    }

    public void created(UUID familyId, String entity, UUID entityId, long version, Object payload) {
        publish(familyId, entity, entityId, DomainChange.Op.C, version, payload);
    }

    public void updated(UUID familyId, String entity, UUID entityId, long version, Object payload) {
        publish(familyId, entity, entityId, DomainChange.Op.U, version, payload);
    }

    public void deleted(UUID familyId, String entity, UUID entityId, long version) {
        publish(familyId, entity, entityId, DomainChange.Op.D, version, Map.of("id", entityId));
    }

    public void publish(UUID familyId, String entity, UUID entityId, DomainChange.Op op, long version,
            Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Los cambios se publican dentro de una transacción");
        }
        events.publishEvent(new DomainChange(familyId, entity, entityId, op, version, CurrentUser.idOrNull(),
                payload, audience));
    }
}

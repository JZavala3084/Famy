package com.famy.shared.events;

import java.util.UUID;

/**
 * Cambio en un dato de una familia. Los módulos de dominio lo publican dentro de su transacción (con
 * {@link ChangePublisher}); el módulo realtime lo guarda en {@code change_log} en esa misma transacción y, tras el
 * commit, lo envía por WebSocket a {@code /topic/families/{familyId}}.
 *
 * @param entity  nombre estable de la entidad, p. ej. {@code shopping.item}
 * @param payload la representación de la entidad tal como la devuelve la API REST ({@code {id}} al borrar)
 */
public record DomainChange(UUID familyId, String entity, UUID entityId, Op op, long version, UUID actorId,
        Object payload) {

    public enum Op {
        C("created"),
        U("updated"),
        D("deleted");

        private final String verb;

        Op(String verb) {
            this.verb = verb;
        }

        public String verb() {
            return verb;
        }
    }

    /** Tipo del evento para los clientes, p. ej. {@code shopping.item.created}. */
    public String type() {
        return entity + "." + op.verb();
    }
}

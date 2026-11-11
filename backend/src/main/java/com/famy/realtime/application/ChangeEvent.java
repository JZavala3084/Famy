package com.famy.realtime.application;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Evento que reciben los clientes por WebSocket y en {@code GET /changes}. {@code id} es el cursor: el cliente guarda
 * el mayor que haya visto y, al reconectar, pide los cambios posteriores.
 */
public record ChangeEvent(long id, String type, UUID familyId, String entity, UUID entityId, String op, long version,
        UUID actor, Instant occurredAt, JsonNode payload) {
}

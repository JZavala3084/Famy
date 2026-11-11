package com.famy.family;

import java.util.UUID;

/** Evento publicado cuando alguien sale de la familia o es expulsado (realtime cierra sus WebSockets). */
public record MemberLeft(UUID familyId, UUID userId) {
}

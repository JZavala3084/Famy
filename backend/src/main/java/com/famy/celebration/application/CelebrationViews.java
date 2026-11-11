package com.famy.celebration.application;

import com.famy.celebration.domain.Celebration.Type;
import com.famy.celebration.domain.GiftIdea;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Representaciones que devuelve la API. */
public final class CelebrationViews {

    private CelebrationViews() {
    }

    /**
     * Una fecha especial. Los cumpleaños de miembros con fecha en su perfil aparecen aunque nadie los haya registrado
     * (entonces {@code id} es nulo); al agregar notas o ideas de regalo se crea el registro.
     *
     * @param personName       para un miembro, su nombre en Famy
     * @param giftIdeas        nulo cuando {@code giftIdeasHidden} (la persona festejada no ve sus regalos)
     * @param nextDate         próxima vez (hoy incluido), en la zona de la familia
     * @param turning          años que cumple (o de aniversario) en {@code nextDate}, si se conoce el año
     */
    public record CelebrationView(UUID id, Type type, String personName, UUID userId, Integer month, Integer day,
            Integer year, String notes, List<GiftIdea> giftIdeas, boolean giftIdeasHidden, LocalDate nextDate,
            Integer daysUntil, Integer turning, UUID createdBy, Instant updatedAt, long version) {
    }
}

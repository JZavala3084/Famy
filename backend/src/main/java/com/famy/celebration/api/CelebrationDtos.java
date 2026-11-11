package com.famy.celebration.api;

import com.famy.celebration.application.CelebrationService.CelebrationInput;
import com.famy.celebration.domain.Celebration.Type;
import com.famy.celebration.domain.GiftIdea;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** DTOs de entrada del módulo celebration. */
public final class CelebrationDtos {

    private CelebrationDtos() {
    }

    public record CelebrationRequest(
            @Schema(description = "BIRTHDAY por defecto") Type type,
            @Size(max = 80) @Schema(description = "Nombre (no aplica a miembros)", example = "Abuela Rosa")
            String personName,
            @Schema(description = "Miembro de la familia: la fecha sale de su perfil") UUID userId,
            @Min(1) @Max(12) Integer month,
            @Min(1) @Max(31) Integer day,
            @Schema(description = "Año de nacimiento o del aniversario (opcional, para saber cuántos cumple)")
            Integer year,
            @Size(max = 1000) String notes,
            @Size(max = 30) @Schema(description = "Reemplaza la lista; omitir para no tocarla")
            List<@NotNull GiftIdea> giftIdeas,
            Long version) {

        CelebrationInput toInput() {
            return new CelebrationInput(type, personName, userId, month, day, year, notes, giftIdeas);
        }
    }
}

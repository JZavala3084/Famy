package com.famy.chore.api;

import com.famy.chore.application.ChoreService.ChoreInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de entrada del módulo chore (las respuestas están en {@code ChoreViews}). */
public final class ChoreDtos {

    private ChoreDtos() {
    }

    public record ChoreRequest(
            @NotBlank @Size(max = 120) @Schema(example = "Sacar la basura") String title,
            @Size(max = 1000) String description,
            @Schema(description = "Fecha límite (de la primera ocurrencia si se repite)", example = "2026-11-16T20:00:00-06:00")
            Instant dueAt,
            @Size(max = 255) @Schema(description = "Repetición RFC 5545, en la zona horaria de la familia",
                    example = "FREQ=WEEKLY;BYDAY=MO,TH") String rrule,
            @Min(0) @Max(1000) Integer points,
            @Schema(description = "Responsable actual; con rotación, por defecto el primero del orden") UUID assigneeId,
            Boolean rotation,
            @Size(max = 20) @Schema(description = "Orden de la rotación (ids de miembros)") List<@NotNull UUID> rotationOrder,
            @Schema(description = "Solo al editar: versión que tiene el cliente; si no coincide responde 409")
            Long version) {

        ChoreInput toInput() {
            return new ChoreInput(title, description, dueAt, rrule, points == null ? 0 : points, assigneeId,
                    Boolean.TRUE.equals(rotation), rotationOrder);
        }
    }

    public record CompleteRequest(
            @Schema(description = "Quién la hizo, si un adulto la registra por otro miembro") UUID memberId,
            Long version) {
    }
}

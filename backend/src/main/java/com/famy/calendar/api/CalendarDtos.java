package com.famy.calendar.api;

import com.famy.calendar.application.CalendarService.EventInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de entrada del módulo calendar (las respuestas están en {@code CalendarViews}). */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    public record EventRequest(
            @NotBlank @Size(max = 120) @Schema(example = "Fútbol de Diego") String title,
            @Size(max = 2000) String description,
            @Size(max = 200) @Schema(example = "Cancha municipal") String location,
            @NotNull @Schema(description = "Inicio de la primera ocurrencia", example = "2026-11-16T16:00:00-06:00")
            Instant startsAt,
            @Schema(description = "Fin de la primera ocurrencia (1 h por defecto). En eventos de todo el día, "
                    + "cualquier momento del último día") Instant endsAt,
            Boolean allDay,
            @Size(max = 255) @Schema(description = "Repetición RFC 5545, en la zona horaria de la familia",
                    example = "FREQ=WEEKLY;BYDAY=TU,TH") String rrule,
            @Size(max = 7) @Schema(example = "#2F5DA8") String color,
            @Size(max = 20) @Schema(description = "Miembros que participan; vacío = toda la familia")
            List<@NotNull UUID> participantIds,
            @Size(max = 5) @Schema(description = "Minutos antes del inicio para avisar", example = "[30, 1440]")
            List<@NotNull Integer> reminderMinutes,
            @Schema(description = "Solo al editar: versión que tiene el cliente; si no coincide responde 409")
            Long version) {

        EventInput toInput() {
            return new EventInput(title, description, location, startsAt, endsAt, Boolean.TRUE.equals(allDay), rrule,
                    color, participantIds, reminderMinutes);
        }
    }
}

package com.famy.calendar.api;

import com.famy.calendar.api.CalendarDtos.EventRequest;
import com.famy.calendar.application.CalendarService;
import com.famy.calendar.application.CalendarViews.EventRange;
import com.famy.calendar.application.CalendarViews.EventView;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/events")
@Tag(name = "Calendario")
public class CalendarController {

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "Eventos y ocurrencias en un rango (máx. 400 días)",
            description = "Expande las repeticiones en la zona horaria de la familia. `participantId` filtra los de "
                    + "esa persona (más los de toda la familia).")
    public EventRange range(@PathVariable UUID familyId, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) UUID participantId) {
        return calendar.range(familyId, CurrentUser.id(), from, to, participantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear evento (un CHILD solo eventos propios)")
    public EventView create(@PathVariable UUID familyId, @Valid @RequestBody EventRequest req) {
        return calendar.create(familyId, CurrentUser.id(), req.toInput());
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Detalle de un evento")
    public EventView get(@PathVariable UUID familyId, @PathVariable UUID eventId) {
        return calendar.get(familyId, CurrentUser.id(), eventId);
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Editar evento o toda la serie (reemplazo completo)")
    public EventView update(@PathVariable UUID familyId, @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest req) {
        return calendar.update(familyId, CurrentUser.id(), eventId, req.toInput(), req.version());
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar evento (toda la serie)")
    public void delete(@PathVariable UUID familyId, @PathVariable UUID eventId) {
        calendar.delete(familyId, CurrentUser.id(), eventId);
    }

    @DeleteMapping("/{eventId}/occurrences/{start}")
    @Operation(summary = "Cancelar solo una ocurrencia (por su inicio)",
            description = "Devuelve el evento actualizado, o 204 si era un evento único y se borró.")
    public ResponseEntity<EventView> cancelOccurrence(@PathVariable UUID familyId, @PathVariable UUID eventId,
            @PathVariable Instant start) {
        EventView view = calendar.cancelOccurrence(familyId, CurrentUser.id(), eventId, start);
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }
}

package com.famy.celebration.api;

import com.famy.celebration.api.CelebrationDtos.CelebrationRequest;
import com.famy.celebration.application.CelebrationService;
import com.famy.celebration.application.CelebrationViews.CelebrationView;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/celebrations")
@Tag(name = "Cumpleaños y fechas especiales")
public class CelebrationController {

    private final CelebrationService celebrations;

    public CelebrationController(CelebrationService celebrations) {
        this.celebrations = celebrations;
    }

    @GetMapping
    @Operation(summary = "Fechas de la familia, de la más próxima a la más lejana",
            description = "Incluye los cumpleaños de los miembros que tienen fecha en su perfil (con `id` nulo si "
                    + "nadie les ha anotado nada). Las ideas de regalo no se le muestran a la persona festejada.")
    public List<CelebrationView> list(@PathVariable UUID familyId) {
        return celebrations.list(familyId, CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una fecha (o notas e ideas de regalo para el cumpleaños de un miembro)")
    public CelebrationView create(@PathVariable UUID familyId, @Valid @RequestBody CelebrationRequest req) {
        return celebrations.create(familyId, CurrentUser.id(), req.toInput());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle")
    public CelebrationView get(@PathVariable UUID familyId, @PathVariable UUID id) {
        return celebrations.get(familyId, CurrentUser.id(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar (reemplazo completo; `giftIdeas` omitido no cambia la lista)")
    public CelebrationView update(@PathVariable UUID familyId, @PathVariable UUID id,
            @Valid @RequestBody CelebrationRequest req) {
        return celebrations.update(familyId, CurrentUser.id(), id, req.toInput(), req.version());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar")
    public void delete(@PathVariable UUID familyId, @PathVariable UUID id) {
        celebrations.delete(familyId, CurrentUser.id(), id);
    }
}

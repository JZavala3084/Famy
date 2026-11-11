package com.famy.chore.api;

import com.famy.chore.api.ChoreDtos.ChoreRequest;
import com.famy.chore.api.ChoreDtos.CompleteRequest;
import com.famy.chore.application.ChoreService;
import com.famy.chore.application.ChoreViews.ChoreView;
import com.famy.chore.application.ChoreViews.CompleteResult;
import com.famy.chore.application.ChoreViews.CompletionView;
import com.famy.chore.application.ChoreViews.Scoreboard;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/families/{familyId}/chores")
@Tag(name = "Tareas")
public class ChoreController {

    private final ChoreService chores;

    public ChoreController(ChoreService chores) {
        this.chores = chores;
    }

    @GetMapping
    @Operation(summary = "Tareas por fecha límite (las sin fecha al final)")
    public List<ChoreView> list(@PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") boolean includeDone,
            @RequestParam(required = false) UUID assigneeId) {
        return chores.list(familyId, CurrentUser.id(), includeDone, assigneeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear tarea (adultos)")
    public ChoreView create(@PathVariable UUID familyId, @Valid @RequestBody ChoreRequest req) {
        return chores.create(familyId, CurrentUser.id(), req.toInput());
    }

    @GetMapping("/scoreboard")
    @Operation(summary = "Puntaje semanal por miembro (semana de lunes a domingo que contiene `date`)")
    public Scoreboard scoreboard(@PathVariable UUID familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return chores.scoreboard(familyId, CurrentUser.id(), date);
    }

    @GetMapping("/{choreId}")
    @Operation(summary = "Detalle de una tarea")
    public ChoreView get(@PathVariable UUID familyId, @PathVariable UUID choreId) {
        return chores.get(familyId, CurrentUser.id(), choreId);
    }

    @PutMapping("/{choreId}")
    @Operation(summary = "Editar tarea (reemplazo completo, adultos)")
    public ChoreView update(@PathVariable UUID familyId, @PathVariable UUID choreId,
            @Valid @RequestBody ChoreRequest req) {
        return chores.update(familyId, CurrentUser.id(), choreId, req.toInput(), req.version());
    }

    @DeleteMapping("/{choreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar tarea (adultos)")
    public void delete(@PathVariable UUID familyId, @PathVariable UUID choreId) {
        chores.delete(familyId, CurrentUser.id(), choreId);
    }

    @PostMapping("/{choreId}/complete")
    @Operation(summary = "Marcar como hecha (CHILD: solo las suyas)",
            description = "Si se repite, avanza a la siguiente fecha y, con rotación, al siguiente miembro.")
    public CompleteResult complete(@PathVariable UUID familyId, @PathVariable UUID choreId,
            @Valid @RequestBody(required = false) CompleteRequest req) {
        return chores.complete(familyId, CurrentUser.id(), choreId, req == null ? null : req.memberId(),
                req == null ? null : req.version());
    }

    @GetMapping("/{choreId}/completions")
    @Operation(summary = "Historial: quién la hizo y cuándo")
    public List<CompletionView> history(@PathVariable UUID familyId, @PathVariable UUID choreId,
            @RequestParam(defaultValue = "50") int limit) {
        return chores.history(familyId, CurrentUser.id(), choreId, limit);
    }

    @DeleteMapping("/{choreId}/completions/{completionId}")
    @Operation(summary = "Deshacer la última marca")
    public ChoreView undo(@PathVariable UUID familyId, @PathVariable UUID choreId,
            @PathVariable UUID completionId) {
        return chores.undoCompletion(familyId, CurrentUser.id(), choreId, completionId);
    }
}

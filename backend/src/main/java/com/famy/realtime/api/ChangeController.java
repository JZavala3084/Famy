package com.famy.realtime.api;

import com.famy.family.FamilyMembership;
import com.famy.realtime.application.ChangeLogService;
import com.famy.realtime.application.ChangeLogService.ChangePage;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/changes")
@Tag(name = "Tiempo real")
@Validated
public class ChangeController {

    public record CursorResponse(long cursor) {
    }

    private final ChangeLogService changes;
    private final FamilyMembership membership;

    public ChangeController(ChangeLogService changes, FamilyMembership membership) {
        this.changes = changes;
        this.membership = membership;
    }

    @GetMapping
    @Operation(summary = "Cambios posteriores a un cursor (para ponerse al día al reconectar)",
            description = "Devuelve hasta `limit` eventos en orden. Si `hasMore` es true, volver a pedir con el "
                    + "`cursor` devuelto. 410 `resync_required` si el cursor es anterior a la retención (30 días).")
    public ChangePage since(@PathVariable UUID familyId, @RequestParam(defaultValue = "0") @Min(0) long since,
            @RequestParam(defaultValue = "200") @Min(1) @Max(ChangeLogService.MAX_PAGE) int limit) {
        membership.requireMember(familyId, CurrentUser.id());
        return changes.since(familyId, since, limit);
    }

    @GetMapping("/cursor")
    @Operation(summary = "Cursor actual (guardarlo antes de la carga inicial)")
    public CursorResponse cursor(@PathVariable UUID familyId) {
        membership.requireMember(familyId, CurrentUser.id());
        return new CursorResponse(changes.currentCursor(familyId));
    }
}

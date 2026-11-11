package com.famy.family.api;

import com.famy.family.application.InvitationService;
import com.famy.family.application.InvitationService.InvitationPreview;
import com.famy.family.application.InvitationService.JoinResult;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Usar una invitación: acepta el código ({@code FAM-K7QX-2M9P}, con o sin guiones) o el token del enlace. */
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitaciones")
public class InvitationController {

    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/{codeOrToken}")
    @Operation(summary = "Vista previa: nombre y foto de la familia")
    public InvitationPreview preview(@PathVariable String codeOrToken) {
        return invitations.preview(codeOrToken, CurrentUser.id());
    }

    @PostMapping("/{codeOrToken}/accept")
    @Operation(summary = "Unirse (200 si entra directo, 202 si queda pendiente de aprobación)")
    public ResponseEntity<JoinResult> accept(@PathVariable String codeOrToken) {
        JoinResult result = invitations.accept(codeOrToken, CurrentUser.id());
        HttpStatus status = result.status() == InvitationService.JoinOutcome.JOINED ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }
}

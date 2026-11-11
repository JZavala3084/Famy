package com.famy.family.api;

import com.famy.family.api.FamilyDtos.ChangeRoleRequest;
import com.famy.family.api.FamilyDtos.CreateFamilyRequest;
import com.famy.family.api.FamilyDtos.CreateInvitationRequest;
import com.famy.family.api.FamilyDtos.FamilyResponse;
import com.famy.family.api.FamilyDtos.InvitationResponse;
import com.famy.family.api.FamilyDtos.JoinRequestResponse;
import com.famy.family.api.FamilyDtos.MemberResponse;
import com.famy.family.api.FamilyDtos.TransferOwnershipRequest;
import com.famy.family.api.FamilyDtos.UpdateFamilyRequest;
import com.famy.family.application.FamilyService;
import com.famy.family.application.FamilyService.CreateFamily;
import com.famy.family.application.FamilyService.UpdateFamily;
import com.famy.family.application.InvitationService;
import com.famy.family.application.InvitationService.CreateInvitation;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
@Tag(name = "Familias")
public class FamilyController {

    private final FamilyService families;
    private final InvitationService invitations;

    public FamilyController(FamilyService families, InvitationService invitations) {
        this.families = families;
        this.invitations = invitations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear familia (quien la crea queda como OWNER)")
    public FamilyResponse create(@Valid @RequestBody CreateFamilyRequest req) {
        return FamilyResponse.of(families.create(CurrentUser.id(),
                new CreateFamily(req.name(), req.avatarUrl(), req.currency(), req.timezone())));
    }

    @GetMapping("/me")
    @Operation(summary = "Familia(s) del usuario")
    public List<FamilyResponse> mine() {
        return families.mine(CurrentUser.id()).stream().map(FamilyResponse::of).toList();
    }

    @GetMapping("/{familyId}")
    @Operation(summary = "Detalle de la familia y sus miembros")
    public FamilyResponse get(@PathVariable UUID familyId) {
        return FamilyResponse.of(families.get(familyId, CurrentUser.id()));
    }

    @PatchMapping("/{familyId}")
    @Operation(summary = "Editar la familia (OWNER; ADMIN solo la moneda)")
    public FamilyResponse update(@PathVariable UUID familyId, @Valid @RequestBody UpdateFamilyRequest req) {
        return FamilyResponse.of(families.update(familyId, CurrentUser.id(), new UpdateFamily(req.name(),
                req.avatarUrl(), req.currency(), req.timezone(), req.membersCanInvite())));
    }

    @PatchMapping("/{familyId}/members/{userId}")
    @Operation(summary = "Cambiar el rol de un miembro")
    public MemberResponse changeRole(@PathVariable UUID familyId, @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest req) {
        return MemberResponse.of(families.changeRole(familyId, CurrentUser.id(), userId, req.role()));
    }

    @DeleteMapping("/{familyId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Expulsar a un miembro, o salir de la familia si es uno mismo")
    public void removeMember(@PathVariable UUID familyId, @PathVariable UUID userId) {
        families.removeMember(familyId, CurrentUser.id(), userId);
    }

    @PostMapping("/{familyId}/transfer-ownership")
    @Operation(summary = "Transferir la propiedad a otro adulto (el OWNER actual queda como ADMIN)")
    public FamilyResponse transferOwnership(@PathVariable UUID familyId,
            @Valid @RequestBody TransferOwnershipRequest req) {
        return FamilyResponse.of(families.transferOwnership(familyId, CurrentUser.id(), req.newOwnerId()));
    }

    @PostMapping("/{familyId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear invitación (código, enlace y QR)")
    public InvitationResponse createInvitation(@PathVariable UUID familyId,
            @Valid @RequestBody(required = false) CreateInvitationRequest req) {
        CreateInvitationRequest r = req == null ? new CreateInvitationRequest(null, null, null, null, null) : req;
        return InvitationResponse.of(invitations.create(familyId, CurrentUser.id(),
                new CreateInvitation(r.role(), r.email(), r.maxUses(), r.expiresInDays(), r.requiresApproval())));
    }

    @GetMapping("/{familyId}/invitations")
    @Operation(summary = "Invitaciones vigentes")
    public List<InvitationResponse> listInvitations(@PathVariable UUID familyId) {
        return invitations.listActive(familyId, CurrentUser.id()).stream().map(InvitationResponse::of).toList();
    }

    @DeleteMapping("/{familyId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revocar invitación")
    public void revokeInvitation(@PathVariable UUID familyId, @PathVariable UUID invitationId) {
        invitations.revoke(familyId, CurrentUser.id(), invitationId);
    }

    @GetMapping("/{familyId}/join-requests")
    @Operation(summary = "Solicitudes de ingreso pendientes")
    public List<JoinRequestResponse> joinRequests(@PathVariable UUID familyId) {
        return invitations.pendingRequests(familyId, CurrentUser.id()).stream().map(JoinRequestResponse::of).toList();
    }

    @PostMapping("/{familyId}/join-requests/{requestId}/approve")
    @Operation(summary = "Aprobar solicitud")
    public JoinRequestResponse approve(@PathVariable UUID familyId, @PathVariable UUID requestId) {
        return JoinRequestResponse.of(invitations.decide(familyId, CurrentUser.id(), requestId, true));
    }

    @PostMapping("/{familyId}/join-requests/{requestId}/reject")
    @Operation(summary = "Rechazar solicitud")
    public JoinRequestResponse reject(@PathVariable UUID familyId, @PathVariable UUID requestId) {
        return JoinRequestResponse.of(invitations.decide(familyId, CurrentUser.id(), requestId, false));
    }
}

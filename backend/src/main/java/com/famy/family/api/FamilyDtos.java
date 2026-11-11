package com.famy.family.api;

import com.famy.family.application.FamilyService.FamilyView;
import com.famy.family.application.FamilyService.MemberView;
import com.famy.family.application.InvitationService;
import com.famy.family.application.InvitationService.CreatedInvitation;
import com.famy.family.application.InvitationService.JoinRequestView;
import com.famy.family.domain.FamilyInvitation;
import com.famy.family.FamilyRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs del módulo family. */
public final class FamilyDtos {

    private FamilyDtos() {
    }

    public record CreateFamilyRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) @Pattern(regexp = "^(https://.*)?$", message = "debe ser una URL https") String avatarUrl,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217")
            @Schema(description = "Moneda del presupuesto familiar; por defecto la del creador (USD)") String currency,
            @Size(max = 64) @Schema(example = "America/Mexico_City") String timezone) {
    }

    public record UpdateFamilyRequest(
            @Size(min = 1, max = 80) String name,
            @Size(max = 500) @Pattern(regexp = "^(https://.*)?$", message = "debe ser una URL https") String avatarUrl,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217") String currency,
            @Size(max = 64) String timezone,
            Boolean membersCanInvite) {
    }

    public record MemberResponse(UUID userId, String displayName, String email, String avatarUrl, String color,
            FamilyRole role, String nickname, Instant joinedAt) {

        static MemberResponse of(MemberView v) {
            var u = v.user();
            var m = v.member();
            return new MemberResponse(m.getUserId(), u == null ? null : u.displayName(), u == null ? null : u.email(),
                    u == null ? null : u.avatarUrl(), u == null ? null : u.color(), m.getRole(), m.getNickname(),
                    m.getJoinedAt());
        }
    }

    public record FamilyResponse(UUID id, String name, String avatarUrl, String currency, String timezone,
            UUID ownerId, boolean membersCanInvite, FamilyRole myRole, List<MemberResponse> members, long version) {

        static FamilyResponse of(FamilyView v) {
            var f = v.family();
            return new FamilyResponse(f.getId(), f.getName(), f.getAvatarUrl(), f.getCurrency(), f.getTimezone(),
                    f.getOwnerId(), f.isMembersCanInvite(), v.myRole(),
                    v.members().stream().map(MemberResponse::of).toList(), f.getVersion());
        }
    }

    public record ChangeRoleRequest(@NotNull FamilyRole role) {
    }

    public record TransferOwnershipRequest(@NotNull UUID newOwnerId) {
    }

    public record CreateInvitationRequest(
            @Schema(description = "Rol con que entra la persona (por defecto MEMBER)") FamilyRole role,
            @Email @Size(max = 254) @Schema(description = "Si se indica, solo esa persona puede usarla y no requiere aprobación") String email,
            @Min(1) @Max(100) Integer maxUses,
            @Min(1) @Max(30) @Schema(description = "Días de vigencia (por defecto 7)") Integer expiresInDays,
            @Schema(description = "Por defecto: sí para código/enlace, no para invitación por correo") Boolean requiresApproval) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InvitationResponse(UUID id, String code, FamilyRole role, String email, Integer maxUses, int uses,
            boolean requiresApproval, Instant expiresAt, UUID createdBy, Instant createdAt,
            @Schema(description = "Solo al crear: token del enlace (no se puede recuperar después)") String token,
            @Schema(description = "Solo al crear: enlace para compartir o mostrar como QR") String link) {

        static InvitationResponse of(FamilyInvitation i) {
            return new InvitationResponse(i.getId(), InvitationService.displayCode(i.getCode()), i.getRole(),
                    i.getEmail(), i.getMaxUses(), i.getUses(), i.isRequiresApproval(), i.getExpiresAt(),
                    i.getCreatedBy(), i.getCreatedAt(), null, null);
        }

        static InvitationResponse of(CreatedInvitation c) {
            var i = c.invitation();
            return new InvitationResponse(i.getId(), c.displayCode(), i.getRole(), i.getEmail(), i.getMaxUses(),
                    i.getUses(), i.isRequiresApproval(), i.getExpiresAt(), i.getCreatedBy(), i.getCreatedAt(),
                    c.token(), c.link());
        }
    }

    public record JoinRequestResponse(UUID id, UUID userId, String displayName, String email, FamilyRole role,
            String status, Instant createdAt) {

        static JoinRequestResponse of(JoinRequestView v) {
            var r = v.request();
            var u = v.user();
            return new JoinRequestResponse(r.getId(), r.getUserId(), u == null ? null : u.displayName(),
                    u == null ? null : u.email(), r.getRole(), r.getStatus().name(), r.getCreatedAt());
        }
    }
}

package com.famy.family.application;

import com.famy.family.domain.Family;
import com.famy.family.domain.FamilyInvitation;
import com.famy.family.domain.FamilyMember;
import com.famy.family.domain.FamilyMemberId;
import com.famy.family.FamilyRole;
import com.famy.family.domain.JoinRequest;
import com.famy.family.domain.JoinRequest.Status;
import com.famy.family.infrastructure.FamilyInvitationRepository;
import com.famy.family.infrastructure.FamilyMemberRepository;
import com.famy.family.infrastructure.FamilyRepository;
import com.famy.family.infrastructure.JoinRequestRepository;
import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.shared.SecureTokens;
import com.famy.shared.audit.AuditService;
import com.famy.shared.config.FamyProperties;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.shared.ratelimit.RateLimiter;
import com.famy.shared.ratelimit.RateLimiter.Policy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitaciones (código de 8 caracteres, enlace, QR, por correo), unirse y solicitudes de ingreso.
 * Decisión P-5: por defecto el código/enlace requiere aprobación y la invitación por correo no.
 */
@Service
public class InvitationService {

    /** Sin caracteres ambiguos (0/O, 1/I/L). */
    static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Pattern CODE_INPUT = Pattern.compile("^(FAM)?[" + CODE_ALPHABET + "]{8}$");
    /** 5 intentos fallidos (código inexistente o vencido) cada 15 minutos por usuario. */
    static final Policy JOIN_FAILURES = new Policy("join-failures", 5, Duration.ofMinutes(15));

    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final FamilyInvitationRepository invitations;
    private final JoinRequestRepository joinRequests;
    private final FamilyAccess access;
    private final FamilyService familyService;
    private final UserDirectory users;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final FamyProperties props;
    private final ChangePublisher changes;

    public InvitationService(FamilyRepository families, FamilyMemberRepository members,
            FamilyInvitationRepository invitations, JoinRequestRepository joinRequests, FamilyAccess access,
            FamilyService familyService, UserDirectory users, RateLimiter rateLimiter, AuditService audit,
            FamyProperties props, ChangePublisher changes) {
        this.families = families;
        this.members = members;
        this.invitations = invitations;
        this.joinRequests = joinRequests;
        this.access = access;
        this.familyService = familyService;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.props = props;
        this.changes = changes;
    }

    public record CreateInvitation(FamilyRole role, String email, Integer maxUses, Integer expiresInDays,
            Boolean requiresApproval) {
    }

    /** {@code token} solo se devuelve al crear la invitación (en la base se guarda su hash). */
    public record CreatedInvitation(FamilyInvitation invitation, String displayCode, String token, String link) {
    }

    public record InvitationPreview(UUID familyId, String familyName, String familyAvatarUrl, long memberCount,
            FamilyRole role, boolean requiresApproval, Instant expiresAt) {
    }

    public enum JoinOutcome {
        JOINED,
        PENDING_APPROVAL
    }

    public record JoinResult(JoinOutcome status, UUID familyId, UUID requestId) {
    }

    public record JoinRequestView(JoinRequest request, UserSummary user) {
    }

    @Transactional
    public CreatedInvitation create(UUID familyId, UUID actorId, CreateInvitation cmd) {
        FamilyMember actor = access.requireMember(familyId, actorId);
        Family family = families.findById(familyId).orElseThrow();
        FamilyRole role = cmd.role() == null ? FamilyRole.MEMBER : cmd.role();
        boolean canInvite = actor.getRole().isManager()
                || (actor.getRole() == FamilyRole.MEMBER && family.isMembersCanInvite());
        if (!canInvite) {
            throw ApiException.forbidden("forbidden", "No tienes permiso para invitar miembros.");
        }
        if (role == FamilyRole.OWNER) {
            throw ApiException.badRequest("invalid_role", "No se puede invitar como dueño.");
        }
        if (role == FamilyRole.ADMIN && actor.getRole() != FamilyRole.OWNER) {
            throw ApiException.forbidden("forbidden", "Solo el dueño puede invitar administradores.");
        }
        String email = cmd.email() == null || cmd.email().isBlank() ? null
                : cmd.email().strip().toLowerCase(Locale.ROOT);
        boolean requiresApproval = cmd.requiresApproval() != null ? cmd.requiresApproval() : email == null;
        Duration ttl = cmd.expiresInDays() == null ? props.auth().invitationTtl()
                : Duration.ofDays(cmd.expiresInDays());
        String token = SecureTokens.newToken();
        FamilyInvitation invitation = invitations.save(new FamilyInvitation(familyId, newUniqueCode(),
                SecureTokens.sha256(token), role, email, cmd.maxUses(), requiresApproval, Instant.now().plus(ttl),
                actorId));
        audit.record("family.invitation_created", actorId, familyId,
                Map.of("invitation", invitation.getId().toString(), "role", role.name()));
        return new CreatedInvitation(invitation, displayCode(invitation.getCode()), token,
                props.baseUrl() + "/join/" + token);
    }

    @Transactional(readOnly = true)
    public List<FamilyInvitation> listActive(UUID familyId, UUID actorId) {
        access.requireRole(familyId, actorId, FamilyRole.OWNER, FamilyRole.ADMIN);
        return invitations.findActiveByFamily(familyId, Instant.now());
    }

    @Transactional
    public void revoke(UUID familyId, UUID actorId, UUID invitationId) {
        FamilyMember actor = access.requireMember(familyId, actorId);
        FamilyInvitation invitation = invitations.findByIdAndFamilyId(invitationId, familyId)
                .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invitación no encontrada."));
        if (!actor.getRole().isManager() && !invitation.getCreatedBy().equals(actorId)) {
            throw ApiException.forbidden("forbidden", "No tienes permiso para revocar esta invitación.");
        }
        invitation.revoke();
        audit.record("family.invitation_revoked", actorId, familyId, Map.of("invitation", invitationId.toString()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public InvitationPreview preview(String codeOrToken, UUID userId) {
        FamilyInvitation invitation = resolve(codeOrToken, userId);
        Family family = families.findById(invitation.getFamilyId()).orElseThrow();
        return new InvitationPreview(family.getId(), family.getName(), family.getAvatarUrl(),
                members.countActive(family.getId()), invitation.getRole(), invitation.isRequiresApproval(),
                invitation.getExpiresAt());
    }

    /**
     * Unirse con un código o enlace. Si la invitación requiere aprobación se crea una solicitud pendiente; si no,
     * el usuario entra directamente con el rol de la invitación.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public JoinResult accept(String codeOrToken, UUID userId) {
        FamilyInvitation invitation = resolve(codeOrToken, userId);
        familyService.requireVerifiedUser(userId);
        UUID familyId = invitation.getFamilyId();
        if (members.findActive(familyId, userId).isPresent()) {
            throw ApiException.conflict("already_member", "Ya eres miembro de esta familia.");
        }
        familyService.requireNoActiveFamily(userId);
        if (invitation.isRequiresApproval()) {
            Optional<JoinRequest> pending = joinRequests.findByFamilyIdAndUserIdAndStatus(familyId, userId,
                    Status.PENDING);
            if (pending.isPresent()) {
                return new JoinResult(JoinOutcome.PENDING_APPROVAL, familyId, pending.get().getId());
            }
            invitation.registerUse();
            JoinRequest request = joinRequests.save(
                    new JoinRequest(familyId, userId, invitation.getId(), invitation.getRole()));
            audit.record("family.join_requested", userId, familyId, Map.of("request", request.getId().toString()));
            return new JoinResult(JoinOutcome.PENDING_APPROVAL, familyId, request.getId());
        }
        invitation.registerUse();
        addMember(familyId, userId, invitation.getRole());
        audit.record("family.joined", userId, familyId, Map.of("invitation", invitation.getId().toString()));
        return new JoinResult(JoinOutcome.JOINED, familyId, null);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestView> pendingRequests(UUID familyId, UUID actorId) {
        access.requireRole(familyId, actorId, FamilyRole.OWNER, FamilyRole.ADMIN);
        List<JoinRequest> pending = joinRequests.findByFamilyIdAndStatusOrderByCreatedAt(familyId, Status.PENDING);
        Map<UUID, UserSummary> summaries = users.findAll(pending.stream().map(JoinRequest::getUserId).toList());
        return pending.stream().map(r -> new JoinRequestView(r, summaries.get(r.getUserId()))).toList();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public JoinRequestView decide(UUID familyId, UUID actorId, UUID requestId, boolean approve) {
        access.requireRole(familyId, actorId, FamilyRole.OWNER, FamilyRole.ADMIN);
        JoinRequest request = joinRequests.findByIdAndFamilyId(requestId, familyId)
                .filter(JoinRequest::isPending)
                .orElseThrow(() -> ApiException.notFound("request_not_found", "Solicitud no encontrada."));
        if (approve) {
            if (!members.findActiveByUser(request.getUserId()).isEmpty()) {
                request.decide(Status.CANCELLED, actorId);
                throw ApiException.conflict("already_in_family", "Esa persona ya pertenece a otra familia.");
            }
            request.decide(Status.APPROVED, actorId);
            addMember(familyId, request.getUserId(), request.getRole());
        } else {
            request.decide(Status.REJECTED, actorId);
        }
        audit.record(approve ? "family.join_approved" : "family.join_rejected", actorId, familyId,
                Map.of("request", requestId.toString(), "user", request.getUserId().toString()));
        return new JoinRequestView(request, users.find(request.getUserId()).orElse(null));
    }

    /** Solicitudes pendientes del propio usuario (para la pantalla «esperando aprobación»). */
    @Transactional(readOnly = true)
    public List<JoinRequest> myPendingRequests(UUID userId) {
        return joinRequests.findByUserIdAndStatus(userId, Status.PENDING);
    }

    private void addMember(UUID familyId, UUID userId, FamilyRole role) {
        FamilyMember member = members.findById(new FamilyMemberId(familyId, userId)).map(previous -> {
            previous.rejoin(role);
            return previous;
        }).orElseGet(() -> new FamilyMember(familyId, userId, role));
        MemberChanges.joined(changes, members.saveAndFlush(member));
        // Al unirse por cualquier vía se cancelan otras solicitudes pendientes del usuario.
        joinRequests.findByUserIdAndStatus(userId, Status.PENDING).stream()
                .filter(r -> !r.getFamilyId().equals(familyId))
                .forEach(r -> r.decide(Status.CANCELLED, userId));
    }

    /** Busca una invitación usable. Cada intento fallido cuenta para el límite anti-adivinanza. */
    private FamilyInvitation resolve(String codeOrToken, UUID userId) {
        String key = userId.toString();
        if (rateLimiter.isExhausted(JOIN_FAILURES, key)) {
            throw ApiException.tooManyRequests();
        }
        Optional<FamilyInvitation> found = lookup(codeOrToken).filter(i -> i.isUsable(Instant.now()));
        if (found.isPresent() && found.get().getEmail() != null) {
            String email = users.find(userId).map(UserSummary::email).orElse(null);
            if (!found.get().getEmail().equalsIgnoreCase(email)) {
                found = Optional.empty();
            }
        }
        if (found.isEmpty()) {
            rateLimiter.tryConsume(JOIN_FAILURES, key);
            throw ApiException.notFound("invitation_not_found", "El código o enlace no es válido o ya venció.");
        }
        return found.get();
    }

    private Optional<FamilyInvitation> lookup(String codeOrToken) {
        if (codeOrToken == null || codeOrToken.length() > 200) {
            return Optional.empty();
        }
        String normalized = codeOrToken.strip().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (CODE_INPUT.matcher(normalized).matches()) {
            String code = normalized.length() == 11 ? normalized.substring(3) : normalized;
            return invitations.findByCode(code);
        }
        return invitations.findByTokenHash(SecureTokens.sha256(codeOrToken.strip()));
    }

    private String newUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = SecureTokens.randomFrom(CODE_ALPHABET, 8);
            if (!invitations.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de invitación único");
    }

    /** {@code K7QX2M9P} → {@code FAM-K7QX-2M9P}. */
    public static String displayCode(String code) {
        return "FAM-" + code.substring(0, 4) + "-" + code.substring(4);
    }
}

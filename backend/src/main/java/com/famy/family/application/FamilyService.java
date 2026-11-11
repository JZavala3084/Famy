package com.famy.family.application;

import com.famy.family.domain.Family;
import com.famy.family.domain.FamilyMember;
import com.famy.family.FamilyRole;
import com.famy.family.infrastructure.FamilyMemberRepository;
import com.famy.family.infrastructure.FamilyRepository;
import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.shared.audit.AuditService;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.family.MemberLeft;
import org.springframework.context.ApplicationEventPublisher;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crear y administrar familias, miembros, roles y propiedad (RF-2.1, RF-2.3, RF-2.4). */
@Service
public class FamilyService {

    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final FamilyAccess access;
    private final UserDirectory users;
    private final AuditService audit;
    private final ChangePublisher changes;
    private final ApplicationEventPublisher events;

    public FamilyService(FamilyRepository families, FamilyMemberRepository members, FamilyAccess access,
            UserDirectory users, AuditService audit, ChangePublisher changes, ApplicationEventPublisher events) {
        this.families = families;
        this.members = members;
        this.access = access;
        this.users = users;
        this.audit = audit;
        this.changes = changes;
        this.events = events;
    }

    public record CreateFamily(String name, String avatarUrl, String currency, String timezone) {
    }

    public record UpdateFamily(String name, String avatarUrl, String currency, String timezone,
            Boolean membersCanInvite) {
    }

    public record MemberView(FamilyMember member, UserSummary user) {
    }

    public record FamilyView(Family family, FamilyRole myRole, List<MemberView> members) {
    }

    @Transactional
    public FamilyView create(UUID userId, CreateFamily cmd) {
        UserSummary user = requireVerifiedUser(userId);
        requireNoActiveFamily(userId);
        String currency = cmd.currency() != null ? currency(cmd.currency()) : user.currency();
        String timezone = cmd.timezone() != null ? timezone(cmd.timezone()) : user.timezone();
        Family family = families.save(new Family(cmd.name(), cmd.avatarUrl(), currency, timezone, userId));
        members.save(new FamilyMember(family.getId(), userId, FamilyRole.OWNER));
        audit.record("family.created", userId, family.getId(), null);
        return view(family.getId(), userId);
    }

    @Transactional(readOnly = true)
    public List<FamilyView> mine(UUID userId) {
        return members.findActiveByUser(userId).stream().map(m -> view(m.getFamilyId(), userId)).toList();
    }

    @Transactional(readOnly = true)
    public FamilyView get(UUID familyId, UUID userId) {
        access.requireMember(familyId, userId);
        return view(familyId, userId);
    }

    /** Editar la familia es del OWNER; un ADMIN solo puede cambiar la moneda del presupuesto familiar. */
    @Transactional
    public FamilyView update(UUID familyId, UUID userId, UpdateFamily cmd) {
        FamilyMember me = access.requireRole(familyId, userId, FamilyRole.OWNER, FamilyRole.ADMIN);
        if (me.getRole() == FamilyRole.ADMIN && (cmd.name() != null || cmd.avatarUrl() != null
                || cmd.timezone() != null || cmd.membersCanInvite() != null)) {
            throw ApiException.forbidden("forbidden", "Solo el dueño de la familia puede editar estos datos.");
        }
        Family family = families.findById(familyId).orElseThrow();
        family.update(cmd.name(), cmd.avatarUrl(), cmd.currency() == null ? null : currency(cmd.currency()),
                cmd.timezone() == null ? null : timezone(cmd.timezone()), cmd.membersCanInvite());
        families.flush();
        return view(familyId, userId);
    }

    /**
     * Cambia el rol de un miembro. OWNER puede asignar ADMIN, MEMBER o CHILD; ADMIN solo cambia entre MEMBER y
     * CHILD a quien no sea ADMIN ni OWNER. La propiedad solo se cambia con {@link #transferOwnership}.
     */
    @Transactional
    public MemberView changeRole(UUID familyId, UUID actorId, UUID targetId, FamilyRole newRole) {
        FamilyMember actor = access.requireRole(familyId, actorId, FamilyRole.OWNER, FamilyRole.ADMIN);
        if (actorId.equals(targetId)) {
            throw ApiException.badRequest("cannot_change_own_role", "No puedes cambiar tu propio rol.");
        }
        if (newRole == FamilyRole.OWNER) {
            throw ApiException.badRequest("use_transfer_ownership", "Para cambiar el dueño usa «Transferir propiedad».");
        }
        FamilyMember target = members.findActive(familyId, targetId)
                .orElseThrow(() -> ApiException.notFound("member_not_found", "Miembro no encontrado."));
        if (target.getRole() == FamilyRole.OWNER) {
            throw ApiException.forbidden("forbidden", "No se puede cambiar el rol del dueño.");
        }
        if (actor.getRole() == FamilyRole.ADMIN
                && (target.getRole() == FamilyRole.ADMIN || newRole == FamilyRole.ADMIN)) {
            throw ApiException.forbidden("forbidden", "Un administrador solo puede asignar los roles MEMBER o CHILD.");
        }
        FamilyRole previous = target.getRole();
        target.changeRole(newRole);
        MemberChanges.updated(changes, members.saveAndFlush(target));
        audit.record("family.role_changed", actorId, familyId,
                Map.of("target", targetId.toString(), "from", previous.name(), "to", newRole.name()));
        return new MemberView(target, users.find(targetId).orElse(null));
    }

    /**
     * Salir (si {@code targetId} es uno mismo) o expulsar. El OWNER no puede salir sin transferir antes la
     * propiedad; un ADMIN solo puede expulsar a MEMBER o CHILD.
     */
    @Transactional
    public void removeMember(UUID familyId, UUID actorId, UUID targetId) {
        FamilyMember actor = access.requireMember(familyId, actorId);
        if (actorId.equals(targetId)) {
            if (actor.getRole() == FamilyRole.OWNER) {
                throw ApiException.conflict("owner_must_transfer",
                        "Transfiere la propiedad de la familia antes de salir.");
            }
            actor.leave();
            audit.record("family.left", actorId, familyId, null);
            MemberChanges.updated(changes, members.saveAndFlush(actor));
            events.publishEvent(new MemberLeft(familyId, actorId));
            return;
        }
        if (!actor.getRole().isManager()) {
            throw ApiException.forbidden("forbidden", "No tienes permiso para expulsar miembros.");
        }
        FamilyMember target = members.findActive(familyId, targetId)
                .orElseThrow(() -> ApiException.notFound("member_not_found", "Miembro no encontrado."));
        if (target.getRole() == FamilyRole.OWNER
                || (actor.getRole() == FamilyRole.ADMIN && target.getRole() == FamilyRole.ADMIN)) {
            throw ApiException.forbidden("forbidden", "No puedes expulsar a este miembro.");
        }
        target.leave();
        audit.record("family.member_removed", actorId, familyId, Map.of("target", targetId.toString()));
        MemberChanges.updated(changes, members.saveAndFlush(target));
        events.publishEvent(new MemberLeft(familyId, targetId));
    }

    /** El OWNER entrega la propiedad a otro adulto de la familia y queda como ADMIN. */
    @Transactional
    public FamilyView transferOwnership(UUID familyId, UUID actorId, UUID newOwnerId) {
        FamilyMember owner = access.requireRole(familyId, actorId, FamilyRole.OWNER);
        if (actorId.equals(newOwnerId)) {
            throw ApiException.badRequest("already_owner", "Ya eres el dueño de la familia.");
        }
        FamilyMember target = members.findActive(familyId, newOwnerId)
                .orElseThrow(() -> ApiException.notFound("member_not_found", "Miembro no encontrado."));
        if (target.getRole() == FamilyRole.CHILD) {
            throw ApiException.badRequest("child_cannot_own", "La propiedad solo puede transferirse a un adulto.");
        }
        target.changeRole(FamilyRole.OWNER);
        owner.changeRole(FamilyRole.ADMIN);
        members.flush();
        MemberChanges.updated(changes, owner);
        MemberChanges.updated(changes, target);
        families.findById(familyId).orElseThrow().transferOwnership(newOwnerId);
        families.flush();
        audit.record("family.ownership_transferred", actorId, familyId, Map.of("to", newOwnerId.toString()));
        return view(familyId, actorId);
    }

    UserSummary requireVerifiedUser(UUID userId) {
        UserSummary user = users.find(userId)
                .orElseThrow(() -> ApiException.unauthorized("unauthenticated", "Inicia sesión para continuar."));
        if (!user.emailVerified()) {
            throw ApiException.forbidden("email_not_verified", "Confirma tu correo antes de crear o unirte a una familia.");
        }
        return user;
    }

    /** MVP: un usuario pertenece a una sola familia activa (el modelo ya permite varias). */
    void requireNoActiveFamily(UUID userId) {
        if (!members.findActiveByUser(userId).isEmpty()) {
            throw ApiException.conflict("already_in_family", "Ya perteneces a una familia.");
        }
    }

    private FamilyView view(UUID familyId, UUID userId) {
        Family family = families.findById(familyId).orElseThrow();
        List<FamilyMember> active = members.findActiveByFamily(familyId);
        Map<UUID, UserSummary> summaries = users.findAll(active.stream().map(FamilyMember::getUserId).toList());
        FamilyRole myRole = active.stream().filter(m -> m.getUserId().equals(userId)).map(FamilyMember::getRole)
                .findFirst().orElse(null);
        return new FamilyView(family, myRole,
                active.stream().map(m -> new MemberView(m, summaries.get(m.getUserId()))).toList());
    }

    static String currency(String code) {
        try {
            return Currency.getInstance(code.toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_currency", "Moneda no válida (usa un código ISO 4217).");
        }
    }

    static String timezone(String tz) {
        try {
            return ZoneId.of(tz).getId();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("invalid_timezone", "Zona horaria no válida.");
        }
    }
}

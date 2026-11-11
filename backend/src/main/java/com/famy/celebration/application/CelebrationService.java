package com.famy.celebration.application;

import com.famy.celebration.application.CelebrationViews.CelebrationView;
import com.famy.celebration.domain.Celebration;
import com.famy.celebration.domain.Celebration.CelebrationData;
import com.famy.celebration.domain.Celebration.Type;
import com.famy.celebration.domain.GiftIdea;
import com.famy.celebration.infrastructure.CelebrationRepository;
import com.famy.family.FamilyMembership;
import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de cumpleaños y fechas especiales. Cualquier miembro puede registrarlas y anotar ideas de regalo; las
 * ideas de regalo de un miembro no se le muestran a él.
 */
@Service
public class CelebrationService {

    static final String CELEBRATION = "celebration";
    static final int MAX_CELEBRATIONS = 500;
    static final int MAX_GIFT_IDEAS = 30;

    private final CelebrationRepository celebrations;
    private final FamilyMembership membership;
    private final UserDirectory users;
    private final ChangePublisher changes;

    public CelebrationService(CelebrationRepository celebrations, FamilyMembership membership, UserDirectory users,
            ChangePublisher changes) {
        this.celebrations = celebrations;
        this.membership = membership;
        this.users = users;
        this.changes = changes;
    }

    /** Datos tal como llegan de la API. */
    public record CelebrationInput(Type type, String personName, UUID userId, Integer month, Integer day,
            Integer year, String notes, List<GiftIdea> giftIdeas) {
    }

    /** Todas las fechas de la familia, de la más próxima a la más lejana. */
    @Transactional(readOnly = true)
    public List<CelebrationView> list(UUID familyId, UUID userId) {
        membership.requireMember(familyId, userId);
        LocalDate today = today(familyId);
        Set<UUID> memberIds = membership.activeMemberIds(familyId);
        Map<UUID, UserSummary> people = users.findAll(memberIds);
        List<CelebrationView> out = new ArrayList<>();
        Set<UUID> withRow = new HashSet<>();
        for (Celebration c : celebrations.findByFamily(familyId)) {
            if (c.getUserId() != null) {
                if (!memberIds.contains(c.getUserId())) {
                    continue; // ex miembro
                }
                withRow.add(c.getUserId());
            }
            out.add(view(c, people.get(c.getUserId()), userId, today));
        }
        for (UUID member : memberIds) {
            UserSummary u = people.get(member);
            if (!withRow.contains(member) && u != null && u.birthday() != null) {
                out.add(memberView(u, userId, today));
            }
        }
        out.sort(Comparator.comparing(CelebrationView::daysUntil, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(v -> v.personName() == null ? "" : v.personName()));
        return out;
    }

    @Transactional(readOnly = true)
    public CelebrationView get(UUID familyId, UUID userId, UUID id) {
        membership.requireMember(familyId, userId);
        Celebration c = require(familyId, id);
        return view(c, c.getUserId() == null ? null : users.find(c.getUserId()).orElse(null), userId,
                today(familyId));
    }

    @Transactional
    public CelebrationView create(UUID familyId, UUID userId, CelebrationInput input) {
        membership.requireMember(familyId, userId);
        if (celebrations.countActive(familyId) >= MAX_CELEBRATIONS) {
            throw ApiException.conflict("too_many_celebrations", "La familia ya tiene el máximo de fechas.");
        }
        CelebrationData data = validate(familyId, userId, input, null);
        if (data.userId() != null && celebrations.findByMember(familyId, data.userId()).isPresent()) {
            throw ApiException.conflict("already_exists", "Ese miembro ya tiene su cumpleaños registrado.");
        }
        Celebration c;
        try {
            c = celebrations.saveAndFlush(new Celebration(familyId, data, userId));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("already_exists", "Ese miembro ya tiene su cumpleaños registrado.");
        }
        changes.created(familyId, CELEBRATION, c.getId(), c.getVersion(), broadcastView(c, familyId));
        return view(c, member(c), userId, today(familyId));
    }

    @Transactional
    public CelebrationView update(UUID familyId, UUID userId, UUID id, CelebrationInput input, Long version) {
        membership.requireMember(familyId, userId);
        Celebration c = require(familyId, id);
        if (version != null && version != c.getVersion()) {
            throw ApiException.conflict("version_conflict",
                    "Otro miembro modificó este dato al mismo tiempo. Vuelve a intentarlo.");
        }
        c.edit(validate(familyId, userId, input, c));
        c = celebrations.saveAndFlush(c);
        changes.updated(familyId, CELEBRATION, c.getId(), c.getVersion(), broadcastView(c, familyId));
        return view(c, member(c), userId, today(familyId));
    }

    @Transactional
    public void delete(UUID familyId, UUID userId, UUID id) {
        membership.requireMember(familyId, userId);
        Celebration c = require(familyId, id);
        c.delete();
        celebrations.saveAndFlush(c);
        changes.deleted(familyId, CELEBRATION, id, c.getVersion());
    }

    // ---------------------------------------------------------------- vistas

    private CelebrationView view(Celebration c, UserSummary member, UUID viewer, LocalDate today) {
        Integer month = c.getMonth();
        Integer day = c.getDay();
        Integer year = c.getYear();
        String name = c.getPersonName();
        if (c.getUserId() != null && member != null) {
            name = member.displayName();
            if (member.birthday() != null) {
                month = member.birthday().getMonthValue();
                day = member.birthday().getDayOfMonth();
                year = member.birthday().getYear();
            }
        }
        boolean hidden = c.getUserId() != null && c.getUserId().equals(viewer);
        return build(c.getId(), c.getType(), name, c.getUserId(), month, day, year, c.getNotes(),
                hidden ? null : c.getGiftIdeas(), hidden, today, c.getCreatedBy(), c.getUpdatedAt(), c.getVersion());
    }

    private static CelebrationView memberView(UserSummary u, UUID viewer, LocalDate today) {
        LocalDate b = u.birthday();
        boolean hidden = u.id().equals(viewer);
        return build(null, Type.BIRTHDAY, u.displayName(), u.id(), b.getMonthValue(), b.getDayOfMonth(), b.getYear(),
                null, hidden ? null : List.of(), hidden, today, null, null, 0);
    }

    /** Lo que viaja en tiempo real: sin ideas de regalo de miembros (cada cliente vuelve a pedir su versión). */
    private CelebrationView broadcastView(Celebration c, UUID familyId) {
        CelebrationView v = view(c, member(c), null, today(familyId));
        if (c.getUserId() == null) {
            return v;
        }
        return new CelebrationView(v.id(), v.type(), v.personName(), v.userId(), v.month(), v.day(), v.year(),
                v.notes(), null, true, v.nextDate(), v.daysUntil(), v.turning(), v.createdBy(), v.updatedAt(),
                v.version());
    }

    private static CelebrationView build(UUID id, Type type, String name, UUID userId, Integer month, Integer day,
            Integer year, String notes, List<GiftIdea> gifts, boolean hidden, LocalDate today, UUID createdBy,
            java.time.Instant updatedAt, long version) {
        LocalDate next = month == null || day == null ? null : AnnualDates.next(month, day, today);
        Integer daysUntil = next == null ? null : (int) ChronoUnit.DAYS.between(today, next);
        Integer turning = next == null || year == null || year >= next.getYear() ? null : next.getYear() - year;
        return new CelebrationView(id, type, name, userId, month, day, year, notes, gifts, hidden, next, daysUntil,
                turning, createdBy, updatedAt, version);
    }

    // ---------------------------------------------------------------- auxiliares

    private CelebrationData validate(UUID familyId, UUID userId, CelebrationInput in, Celebration existing) {
        Type type = in.type() == null ? Type.BIRTHDAY : in.type();
        UUID member = existing != null ? existing.getUserId() : in.userId();
        List<GiftIdea> gifts = in.giftIdeas() == null ? null : in.giftIdeas().stream()
                .filter(g -> g != null && g.text() != null && !g.text().isBlank())
                .map(g -> new GiftIdea(g.text().strip(), g.done()))
                .toList();
        if (gifts != null && (gifts.size() > MAX_GIFT_IDEAS || gifts.stream().anyMatch(g -> g.text().length() > 200))) {
            throw ApiException.badRequest("invalid_gift_ideas", "Hasta 30 ideas de regalo de 200 caracteres.");
        }
        if (member != null) {
            if (!membership.activeMemberIds(familyId).contains(member)) {
                throw ApiException.badRequest("invalid_member", "Esa persona no es miembro de la familia.");
            }
            if (member.equals(userId) && existing != null) {
                // La persona festejada no ve sus ideas de regalo: tampoco puede reemplazarlas sin querer.
                gifts = existing.getGiftIdeas();
            }
            return new CelebrationData(Type.BIRTHDAY, null, member, null, null, null, in.notes(),
                    gifts == null ? (existing == null ? List.of() : existing.getGiftIdeas()) : gifts);
        }
        if (in.personName() == null || in.personName().isBlank()) {
            throw ApiException.badRequest("name_required", "Escribe el nombre de la persona o del festejo.");
        }
        if (in.month() == null || in.day() == null || !AnnualDates.valid(in.month(), in.day())) {
            throw ApiException.badRequest("invalid_date", "La fecha no es válida.");
        }
        if (in.year() != null && (in.year() < 1900 || in.year() > today(familyId).getYear())) {
            throw ApiException.badRequest("invalid_year", "El año no es válido.");
        }
        if (in.year() != null && in.month() == 2 && in.day() == 29 && !java.time.Year.isLeap(in.year())) {
            throw ApiException.badRequest("invalid_date", "Ese año no tuvo 29 de febrero.");
        }
        return new CelebrationData(type, in.personName(), null, in.month(), in.day(), in.year(), in.notes(),
                gifts == null ? (existing == null ? List.of() : existing.getGiftIdeas()) : gifts);
    }

    private UserSummary member(Celebration c) {
        return c.getUserId() == null ? null : users.find(c.getUserId()).orElse(null);
    }

    private Celebration require(UUID familyId, UUID id) {
        return celebrations.findActive(familyId, id)
                .orElseThrow(() -> ApiException.notFound("celebration_not_found", "Fecha no encontrada."));
    }

    private LocalDate today(UUID familyId) {
        return LocalDate.now(membership.timezone(familyId));
    }
}

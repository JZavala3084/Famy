package com.famy.chore.application;

import com.famy.chore.application.ChoreViews.ChoreView;
import com.famy.chore.application.ChoreViews.CompleteResult;
import com.famy.chore.application.ChoreViews.CompletionView;
import com.famy.chore.application.ChoreViews.MemberScore;
import com.famy.chore.application.ChoreViews.Scoreboard;
import com.famy.chore.domain.Chore;
import com.famy.chore.domain.Chore.ChoreData;
import com.famy.chore.domain.ChoreCompletion;
import com.famy.chore.infrastructure.ChoreCompletionRepository;
import com.famy.chore.infrastructure.ChoreRepository;
import com.famy.family.FamilyMembership;
import com.famy.family.FamilyRole;
import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.shared.recurrence.Recurrence;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de tareas del hogar. Adultos (OWNER, ADMIN, MEMBER) crean, editan y marcan cualquier tarea; un CHILD
 * solo marca las que tiene asignadas (ver docs/06-familias.md#roles-y-permisos).
 */
@Service
public class ChoreService {

    static final String CHORE = "chore";
    static final String COMPLETION = "chore.completion";
    static final int MAX_CHORES = 300;

    private final ChoreRepository chores;
    private final ChoreCompletionRepository completions;
    private final FamilyMembership membership;
    private final UserDirectory users;
    private final ChangePublisher changes;

    public ChoreService(ChoreRepository chores, ChoreCompletionRepository completions, FamilyMembership membership,
            UserDirectory users, ChangePublisher changes) {
        this.chores = chores;
        this.completions = completions;
        this.membership = membership;
        this.users = users;
        this.changes = changes;
    }

    /** Datos tal como llegan de la API (sin validar contra la familia). */
    public record ChoreInput(String title, String description, Instant dueAt, String rrule, int points,
            UUID assigneeId, boolean rotation, List<UUID> rotationOrder) {
    }

    @Transactional(readOnly = true)
    public List<ChoreView> list(UUID familyId, UUID userId, boolean includeDone, UUID assigneeId) {
        membership.requireMember(familyId, userId);
        return chores.findByFamily(familyId, includeDone, assigneeId).stream().map(ChoreView::of).toList();
    }

    @Transactional(readOnly = true)
    public ChoreView get(UUID familyId, UUID userId, UUID choreId) {
        membership.requireMember(familyId, userId);
        return ChoreView.of(require(familyId, choreId));
    }

    @Transactional
    public ChoreView create(UUID familyId, UUID userId, ChoreInput input) {
        requireAdult(familyId, userId);
        if (chores.countActive(familyId) >= MAX_CHORES) {
            throw ApiException.conflict("too_many_chores", "La familia ya tiene el máximo de tareas.");
        }
        Chore chore = chores.saveAndFlush(new Chore(familyId, validate(familyId, input), userId));
        ChoreView view = ChoreView.of(chore);
        changes.created(familyId, CHORE, chore.getId(), chore.getVersion(), view);
        return view;
    }

    @Transactional
    public ChoreView update(UUID familyId, UUID userId, UUID choreId, ChoreInput input, Long version) {
        requireAdult(familyId, userId);
        Chore chore = require(familyId, choreId);
        if (version != null && version != chore.getVersion()) {
            throw versionConflict();
        }
        chore.edit(validate(familyId, input));
        return publish(chores.saveAndFlush(chore));
    }

    @Transactional
    public void delete(UUID familyId, UUID userId, UUID choreId) {
        requireAdult(familyId, userId);
        Chore chore = require(familyId, choreId);
        chore.delete();
        chores.saveAndFlush(chore);
        changes.deleted(familyId, CHORE, choreId, chore.getVersion());
    }

    /**
     * RF-7.3: marcar como hecha. Si se repite, pasa a la siguiente ocurrencia (posterior a la fecha límite y a ahora,
     * para no dejar atrasos encadenados) y, si rota, al siguiente miembro activo de la rotación.
     *
     * @param memberId quién la hizo, si un adulto la registra por otro (por defecto, quien llama)
     */
    @Transactional
    public CompleteResult complete(UUID familyId, UUID userId, UUID choreId, UUID memberId, Long version) {
        FamilyRole role = membership.requireMember(familyId, userId);
        Chore chore = require(familyId, choreId);
        if (version != null && version != chore.getVersion()) {
            throw versionConflict();
        }
        UUID doer = memberId == null ? userId : memberId;
        if (role == FamilyRole.CHILD && (!doer.equals(userId) || !userId.equals(chore.getCurrentAssigneeId()))) {
            throw ApiException.forbidden("forbidden", "Solo puedes marcar las tareas que tienes asignadas.");
        }
        Set<UUID> active = membership.activeMemberIds(familyId);
        if (!active.contains(doer)) {
            throw ApiException.badRequest("invalid_member", "Esa persona no es miembro de la familia.");
        }
        if (chore.isDone()) {
            throw ApiException.conflict("already_done", "Esta tarea ya está hecha.");
        }
        ZoneId zone = membership.timezone(familyId);
        Instant now = Instant.now();
        LocalDate occurrence = (chore.getDueAt() == null ? now : chore.getDueAt()).atZone(zone).toLocalDate();
        ChoreCompletion completion = completions.saveAndFlush(
                new ChoreCompletion(chore, doer, userId, now, occurrence));

        Instant nextDue = null;
        if (chore.getRrule() != null) {
            Instant after = chore.getDueAt().isAfter(now) ? chore.getDueAt() : now;
            nextDue = Recurrence.nextAfter(chore.getRrule(), chore.getRecurrenceStart(), zone, after).orElse(null);
        }
        UUID nextAssignee = chore.getCurrentAssigneeId();
        if (chore.isRotation() && nextDue != null) {
            nextAssignee = nextInRotation(chore.getRotationOrder(), chore.getCurrentAssigneeId(), active);
        }
        chore.advance(nextDue, nextAssignee, now);
        ChoreView view = publish(chores.saveAndFlush(chore));
        CompletionView completionView = CompletionView.of(completion);
        changes.created(familyId, COMPLETION, completion.getId(), 0, completionView);
        return new CompleteResult(view, completionView);
    }

    /** Deshacer la última marca (por error). Puede hacerlo quien la registró, quien la hizo o un OWNER/ADMIN. */
    @Transactional
    public ChoreView undoCompletion(UUID familyId, UUID userId, UUID choreId, UUID completionId) {
        FamilyRole role = membership.requireMember(familyId, userId);
        Chore chore = require(familyId, choreId);
        ChoreCompletion last = completions.findLatest(choreId)
                .filter(c -> c.getId().equals(completionId))
                .orElseThrow(() -> ApiException.conflict("not_latest_completion",
                        "Solo se puede deshacer la última vez que se marcó la tarea."));
        boolean own = userId.equals(last.getRecordedBy()) || userId.equals(last.getCompletedBy());
        if (!own && !role.isManager()) {
            throw ApiException.forbidden("forbidden", "No puedes deshacer esta marca.");
        }
        chore.restore(last.getPreviousDueAt(), last.getPreviousAssigneeId());
        completions.delete(last);
        ChoreView view = publish(chores.saveAndFlush(chore));
        changes.deleted(familyId, COMPLETION, completionId, 0);
        return view;
    }

    @Transactional(readOnly = true)
    public List<CompletionView> history(UUID familyId, UUID userId, UUID choreId, int limit) {
        membership.requireMember(familyId, userId);
        require(familyId, choreId);
        return completions.findByChore(choreId, Limit.of(Math.clamp(limit, 1, 200))).stream()
                .map(CompletionView::of).toList();
    }

    /** RF-7.4: puntos por miembro en la semana (lunes a domingo, zona de la familia) que contiene {@code date}. */
    @Transactional(readOnly = true)
    public Scoreboard scoreboard(UUID familyId, UUID userId, LocalDate date) {
        membership.requireMember(familyId, userId);
        ZoneId zone = membership.timezone(familyId);
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        LocalDate start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        List<ChoreCompletion> done = completions.findByFamilyBetween(familyId, start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());

        Map<UUID, int[]> totals = new LinkedHashMap<>();
        membership.activeMemberIds(familyId).forEach(id -> totals.put(id, new int[2]));
        for (ChoreCompletion c : done) {
            int[] t = totals.computeIfAbsent(c.getCompletedBy(), id -> new int[2]);
            t[0] += c.getPoints();
            t[1]++;
        }
        Map<UUID, UserSummary> names = users.findAll(totals.keySet());
        List<MemberScore> scores = totals.entrySet().stream()
                .map(e -> new MemberScore(e.getKey(),
                        names.containsKey(e.getKey()) ? names.get(e.getKey()).displayName() : null,
                        e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(MemberScore::points).reversed()
                        .thenComparing(MemberScore::completions, Comparator.reverseOrder())
                        .thenComparing(s -> s.displayName() == null ? "" : s.displayName()))
                .toList();
        return new Scoreboard(start, end, scores);
    }

    // ---------------------------------------------------------------- auxiliares

    /** Siguiente miembro activo después de {@code current} en el orden de rotación (se saltan los ex miembros). */
    static UUID nextInRotation(List<UUID> order, UUID current, Set<UUID> active) {
        if (order.isEmpty()) {
            return current;
        }
        int start = order.indexOf(current);
        for (int k = 1; k <= order.size(); k++) {
            UUID candidate = order.get(Math.floorMod(start + k, order.size()));
            if (active.contains(candidate)) {
                return candidate;
            }
        }
        return current;
    }

    private ChoreData validate(UUID familyId, ChoreInput in) {
        String rrule = in.rrule() == null || in.rrule().isBlank() ? null : Recurrence.validate(in.rrule());
        if (rrule != null && in.dueAt() == null) {
            throw ApiException.badRequest("due_required", "Una tarea que se repite necesita fecha límite.");
        }
        Set<UUID> active = membership.activeMemberIds(familyId);
        List<UUID> order = in.rotationOrder() == null ? List.of() : in.rotationOrder();
        UUID assignee = in.assigneeId();
        if (in.rotation()) {
            if (order.size() < 2 || new HashSet<>(order).size() != order.size()) {
                throw ApiException.badRequest("invalid_rotation",
                        "La rotación necesita al menos dos miembros distintos.");
            }
            if (!active.containsAll(order)) {
                throw ApiException.badRequest("invalid_member", "La rotación incluye a alguien que no es miembro.");
            }
            if (assignee == null) {
                assignee = order.getFirst();
            } else if (!order.contains(assignee)) {
                throw ApiException.badRequest("invalid_rotation", "El responsable debe estar en la rotación.");
            }
        }
        if (assignee != null && !active.contains(assignee)) {
            throw ApiException.badRequest("invalid_member", "El responsable no es miembro de la familia.");
        }
        return new ChoreData(in.title(), in.description(), in.dueAt(), rrule, in.points(), assignee, in.rotation(),
                order);
    }

    private void requireAdult(UUID familyId, UUID userId) {
        if (membership.requireMember(familyId, userId) == FamilyRole.CHILD) {
            throw ApiException.forbidden("forbidden", "Pide a un adulto que cree o edite la tarea.");
        }
    }

    private Chore require(UUID familyId, UUID choreId) {
        return chores.findActive(familyId, choreId)
                .orElseThrow(() -> ApiException.notFound("chore_not_found", "Tarea no encontrada."));
    }

    private ChoreView publish(Chore chore) {
        ChoreView view = ChoreView.of(chore);
        changes.updated(chore.getFamilyId(), CHORE, chore.getId(), chore.getVersion(), view);
        return view;
    }

    private static ApiException versionConflict() {
        return ApiException.conflict("version_conflict",
                "Otro miembro modificó este dato al mismo tiempo. Vuelve a intentarlo.");
    }
}

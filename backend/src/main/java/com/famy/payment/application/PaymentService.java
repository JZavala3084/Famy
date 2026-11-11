package com.famy.payment.application;

import com.famy.family.FamilyMembership;
import com.famy.family.FamilyRole;
import com.famy.payment.application.PaymentViews.OccurrenceRange;
import com.famy.payment.application.PaymentViews.OccurrenceView;
import com.famy.payment.application.PaymentViews.PaymentView;
import com.famy.payment.domain.Payment;
import com.famy.payment.domain.Payment.PaymentData;
import com.famy.payment.domain.PaymentOccurrence;
import com.famy.payment.infrastructure.PaymentOccurrenceRepository;
import com.famy.payment.infrastructure.PaymentRepository;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.shared.files.FileStorage;
import com.famy.shared.files.FileStorage.StoredFile;
import com.famy.shared.recurrence.Recurrence;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del calendario de pagos. Ver: adultos (OWNER, ADMIN, MEMBER); crear, editar y borrar pagos: OWNER y
 * ADMIN; marcar pagado y subir comprobantes: adultos. Un CHILD no los ve (decisión P-4).
 */
@Service
public class PaymentService {

    static final String PAYMENT = "payment";
    static final String OCCURRENCE = "payment.occurrence";
    static final int MAX_PAYMENTS = 300;
    static final int MAX_OCCURRENCES = 2_000;
    static final int LOOKBACK_DAYS = 180;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");

    private final PaymentRepository payments;
    private final PaymentOccurrenceRepository paid;
    private final FamilyMembership membership;
    private final ChangePublisher changes;
    private final FileStorage files;

    public PaymentService(PaymentRepository payments, PaymentOccurrenceRepository paid, FamilyMembership membership,
            ChangePublisher changes, FileStorage files) {
        this.payments = payments;
        this.paid = paid;
        this.membership = membership;
        this.changes = changes.adults();
        this.files = files;
    }

    /** Datos tal como llegan de la API (sin validar contra la familia). */
    public record PaymentInput(String name, BigDecimal amount, String currency, String rrule, LocalDate firstDueOn,
            Integer remindDaysBefore, UUID responsibleId, String category, String notes) {
    }

    public record PayInput(BigDecimal amountPaid, LocalDate paidOn, String note) {
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(UUID familyId, UUID userId) {
        requireAdult(familyId, userId);
        LocalDate today = today(familyId);
        List<Payment> all = payments.findByFamily(familyId);
        Map<UUID, Set<LocalDate>> paidDates = paidDates(all, today.minusDays(LOOKBACK_DAYS), today.plusYears(2));
        return all.stream().map(p -> view(p, today, paidDates.getOrDefault(p.getId(), Set.of()))).toList();
    }

    @Transactional(readOnly = true)
    public PaymentView get(UUID familyId, UUID userId, UUID paymentId) {
        requireAdult(familyId, userId);
        return view(require(familyId, paymentId), today(familyId));
    }

    /** Vencimientos de todos los pagos en {@code [from, to]} (máx. 400 días). */
    @Transactional(readOnly = true)
    public OccurrenceRange occurrences(UUID familyId, UUID userId, LocalDate from, LocalDate to) {
        requireAdult(familyId, userId);
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 400) {
            throw ApiException.badRequest("invalid_range", "El rango de fechas no es válido (máx. 400 días).");
        }
        LocalDate today = today(familyId);
        List<Payment> all = payments.findByFamily(familyId);
        Map<UUID, Payment> byId = all.stream().collect(Collectors.toMap(Payment::getId, p -> p));
        Map<String, PaymentOccurrence> paidByKey = new HashMap<>();
        for (PaymentOccurrence o : paid.findByFamilyBetween(familyId, from, to)) {
            paidByKey.put(o.getPaymentId() + "|" + o.getDueOn(), o);
        }
        List<OccurrenceView> out = new ArrayList<>();
        for (Payment p : all) {
            for (LocalDate due : dueDates(p, from, to, MAX_OCCURRENCES)) {
                PaymentOccurrence o = paidByKey.remove(p.getId() + "|" + due);
                out.add(o == null ? OccurrenceView.unpaid(p, due, today) : OccurrenceView.paid(p, o));
            }
        }
        // Pagados cuyo vencimiento ya no está en la regla (se editó el pago): se siguen mostrando.
        paidByKey.values().forEach(o -> {
            Payment p = byId.get(o.getPaymentId());
            if (p != null) {
                out.add(OccurrenceView.paid(p, o));
            }
        });
        out.sort(Comparator.comparing(OccurrenceView::dueOn).thenComparing(OccurrenceView::name));
        return new OccurrenceRange(from, to, today, out.size() > MAX_OCCURRENCES ? out.subList(0, MAX_OCCURRENCES) : out);
    }

    @Transactional
    public PaymentView create(UUID familyId, UUID userId, PaymentInput input) {
        requireManager(familyId, userId);
        if (payments.countActive(familyId) >= MAX_PAYMENTS) {
            throw ApiException.conflict("too_many_payments", "La familia ya tiene el máximo de pagos.");
        }
        Payment p = payments.saveAndFlush(new Payment(familyId, validate(familyId, input), userId));
        PaymentView view = view(p, today(familyId));
        changes.created(familyId, PAYMENT, p.getId(), p.getVersion(), view);
        return view;
    }

    @Transactional
    public PaymentView update(UUID familyId, UUID userId, UUID paymentId, PaymentInput input, Long version) {
        requireManager(familyId, userId);
        Payment p = require(familyId, paymentId);
        if (version != null && version != p.getVersion()) {
            throw versionConflict();
        }
        p.edit(validate(familyId, input));
        return publish(payments.saveAndFlush(p));
    }

    @Transactional
    public void delete(UUID familyId, UUID userId, UUID paymentId) {
        requireManager(familyId, userId);
        Payment p = require(familyId, paymentId);
        p.delete();
        payments.saveAndFlush(p);
        changes.deleted(familyId, PAYMENT, paymentId, p.getVersion());
    }

    /** RF-8.2: marcar un vencimiento como pagado. */
    @Transactional
    public OccurrenceView pay(UUID familyId, UUID userId, UUID paymentId, LocalDate dueOn, PayInput input) {
        requireAdult(familyId, userId);
        Payment p = require(familyId, paymentId);
        requireOccurrence(p, dueOn);
        if (paid.find(paymentId, dueOn).isPresent()) {
            throw ApiException.conflict("already_paid", "Ese vencimiento ya está pagado.");
        }
        LocalDate today = today(familyId);
        BigDecimal amount = input.amountPaid() == null ? p.getAmount() : money(input.amountPaid());
        LocalDate paidOn = input.paidOn() == null ? today : input.paidOn();
        if (paidOn.isAfter(today.plusDays(1)) || paidOn.getYear() < 2000) {
            throw ApiException.badRequest("invalid_paid_on", "La fecha de pago no es válida.");
        }
        PaymentOccurrence o = paid.saveAndFlush(new PaymentOccurrence(p, dueOn, paidOn, userId, amount,
                input.note()));
        OccurrenceView view = OccurrenceView.paid(p, o);
        changes.created(familyId, OCCURRENCE, o.getId(), o.getVersion(), view);
        publish(p);
        return view;
    }

    /** Deshacer «pagado» (por error). Quien lo marcó o un OWNER/ADMIN. */
    @Transactional
    public OccurrenceView unpay(UUID familyId, UUID userId, UUID paymentId, LocalDate dueOn) {
        FamilyRole role = requireAdult(familyId, userId);
        Payment p = require(familyId, paymentId);
        PaymentOccurrence o = paid.find(paymentId, dueOn)
                .orElseThrow(() -> ApiException.notFound("occurrence_not_found", "Ese vencimiento no está pagado."));
        if (!role.isManager() && !userId.equals(o.getPaidBy())) {
            throw ApiException.forbidden("forbidden", "Solo quien lo marcó o un administrador puede deshacerlo.");
        }
        if (o.getReceiptFileId() != null) {
            files.delete(familyId, o.getReceiptFileId());
        }
        paid.delete(o);
        paid.flush();
        changes.deleted(familyId, OCCURRENCE, o.getId(), o.getVersion());
        publish(p);
        return OccurrenceView.unpaid(p, dueOn, today(familyId));
    }

    /** RF-8.4: adjuntar (o reemplazar) el comprobante de un vencimiento pagado. */
    @Transactional
    public OccurrenceView attachReceipt(UUID familyId, UUID userId, UUID paymentId, LocalDate dueOn,
            String filename, byte[] content) {
        requireAdult(familyId, userId);
        Payment p = require(familyId, paymentId);
        PaymentOccurrence o = paid.find(paymentId, dueOn)
                .orElseThrow(() -> ApiException.conflict("not_paid", "Marca el pago como pagado antes de adjuntar."));
        StoredFile file = files.store(familyId, userId, filename, content);
        UUID previous = o.getReceiptFileId();
        o.attachReceipt(file.id());
        paid.saveAndFlush(o);
        if (previous != null) {
            files.delete(familyId, previous);
        }
        OccurrenceView view = OccurrenceView.paid(p, o);
        changes.updated(familyId, OCCURRENCE, o.getId(), o.getVersion(), view);
        return view;
    }

    @Transactional(readOnly = true)
    public StoredFile receipt(UUID familyId, UUID userId, UUID paymentId, LocalDate dueOn) {
        requireAdult(familyId, userId);
        require(familyId, paymentId);
        UUID fileId = paid.find(paymentId, dueOn).map(PaymentOccurrence::getReceiptFileId)
                .orElseThrow(() -> ApiException.notFound("receipt_not_found", "No hay comprobante."));
        return files.find(familyId, fileId)
                .orElseThrow(() -> ApiException.notFound("receipt_not_found", "No hay comprobante."));
    }

    public java.nio.file.Path receiptPath(StoredFile file) {
        return files.path(file);
    }

    // ---------------------------------------------------------------- auxiliares

    /** Vencimientos de un pago en {@code [from, to]}. */
    static List<LocalDate> dueDates(Payment p, LocalDate from, LocalDate to, int max) {
        if (p.getRrule() == null) {
            LocalDate d = p.getFirstDueOn();
            return d.isBefore(from) || d.isAfter(to) ? List.of() : List.of(d);
        }
        if (to.isBefore(p.getFirstDueOn())) {
            return List.of();
        }
        return Recurrence.datesBetween(p.getRrule(), p.getFirstDueOn(), from, to, max);
    }

    private PaymentView view(Payment p, LocalDate today) {
        return view(p, today, paidDates(List.of(p), today.minusDays(LOOKBACK_DAYS), today.plusYears(2))
                .getOrDefault(p.getId(), Set.of()));
    }

    private static PaymentView view(Payment p, LocalDate today, Set<LocalDate> paidDates) {
        LocalDate next = null;
        int overdue = 0;
        for (LocalDate d : dueDates(p, today.minusDays(LOOKBACK_DAYS), today.plusYears(2), 500)) {
            if (paidDates.contains(d)) {
                continue;
            }
            if (next == null) {
                next = d;
            }
            if (d.isBefore(today)) {
                overdue++;
            } else {
                break;
            }
        }
        return PaymentView.of(p, next, overdue);
    }

    private Map<UUID, Set<LocalDate>> paidDates(List<Payment> list, LocalDate from, LocalDate to) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return paid.findByPaymentsBetween(list.stream().map(Payment::getId).toList(), from, to).stream()
                .collect(Collectors.groupingBy(PaymentOccurrence::getPaymentId,
                        Collectors.mapping(PaymentOccurrence::getDueOn, Collectors.toSet())));
    }

    private void requireOccurrence(Payment p, LocalDate dueOn) {
        if (dueOn == null || !dueDates(p, dueOn, dueOn, 1).contains(dueOn)) {
            throw ApiException.notFound("occurrence_not_found", "Ese vencimiento no existe.");
        }
    }

    private PaymentData validate(UUID familyId, PaymentInput in) {
        if (in.firstDueOn() == null) {
            throw ApiException.badRequest("due_required", "Indica la fecha de vencimiento.");
        }
        String rrule = in.rrule() == null || in.rrule().isBlank() ? null : Recurrence.validate(in.rrule());
        String currency = in.currency() == null || in.currency().isBlank() ? membership.currency(familyId)
                : in.currency().strip().toUpperCase();
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_currency", "Moneda no válida (usa un código ISO 4217).");
        }
        UUID responsible = in.responsibleId();
        if (responsible != null && !membership.activeAdultIds(familyId).contains(responsible)) {
            throw ApiException.badRequest("invalid_member", "El responsable debe ser un adulto de la familia.");
        }
        int remind = in.remindDaysBefore() == null ? 3 : in.remindDaysBefore();
        if (remind < 0 || remind > 30) {
            throw ApiException.badRequest("invalid_remind", "El aviso debe ser entre 0 y 30 días antes.");
        }
        return new PaymentData(in.name(), money(in.amount()), currency, rrule, in.firstDueOn(), remind, responsible,
                in.category(), in.notes());
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw ApiException.badRequest("invalid_amount", "El monto no es válido.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private FamilyRole requireAdult(UUID familyId, UUID userId) {
        FamilyRole role = membership.requireMember(familyId, userId);
        if (role == FamilyRole.CHILD) {
            throw ApiException.forbidden("forbidden", "Los pagos solo los ven los adultos de la familia.");
        }
        return role;
    }

    private void requireManager(UUID familyId, UUID userId) {
        if (!requireAdult(familyId, userId).isManager()) {
            throw ApiException.forbidden("forbidden", "Solo un administrador puede crear o editar pagos.");
        }
    }

    private Payment require(UUID familyId, UUID paymentId) {
        return payments.findActive(familyId, paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "Pago no encontrado."));
    }

    private LocalDate today(UUID familyId) {
        return LocalDate.now(membership.timezone(familyId));
    }

    private PaymentView publish(Payment p) {
        PaymentView view = view(p, today(p.getFamilyId()));
        changes.updated(p.getFamilyId(), PAYMENT, p.getId(), p.getVersion(), view);
        return view;
    }

    private static ApiException versionConflict() {
        return ApiException.conflict("version_conflict",
                "Otro miembro modificó este dato al mismo tiempo. Vuelve a intentarlo.");
    }
}

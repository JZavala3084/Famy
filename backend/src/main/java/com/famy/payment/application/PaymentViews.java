package com.famy.payment.application;

import com.famy.payment.domain.Payment;
import com.famy.payment.domain.PaymentOccurrence;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Representaciones que devuelve la API y que viajan como {@code payload} en los eventos en tiempo real. */
public final class PaymentViews {

    private PaymentViews() {
    }

    public enum Status {
        PENDING,
        PAID,
        OVERDUE
    }

    /**
     * @param nextDueOn    primer vencimiento sin pagar (incluye los vencidos de los últimos 6 meses), o nulo si no hay
     * @param overdueCount vencimientos sin pagar con fecha anterior a hoy (últimos 6 meses)
     */
    public record PaymentView(UUID id, String name, BigDecimal amount, String currency, String rrule,
            LocalDate firstDueOn, int remindDaysBefore, UUID responsibleId, String category, String notes,
            LocalDate nextDueOn, int overdueCount, UUID createdBy, Instant createdAt, Instant updatedAt,
            long version) {

        static PaymentView of(Payment p, LocalDate nextDueOn, int overdueCount) {
            return new PaymentView(p.getId(), p.getName(), p.getAmount(), p.getCurrency(), p.getRrule(),
                    p.getFirstDueOn(), p.getRemindDaysBefore(), p.getResponsibleId(), p.getCategory(), p.getNotes(),
                    nextDueOn, overdueCount, p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }

    /** Un vencimiento: pagado (con {@code id}) o calculado desde la regla (pendiente / vencido, {@code id} nulo). */
    public record OccurrenceView(UUID id, UUID paymentId, String name, BigDecimal amount, String currency,
            LocalDate dueOn, Status status, UUID responsibleId, String category, LocalDate paidOn, UUID paidBy,
            BigDecimal amountPaid, String note, UUID receiptFileId) {

        static OccurrenceView paid(Payment p, PaymentOccurrence o) {
            return new OccurrenceView(o.getId(), p.getId(), p.getName(), p.getAmount(), p.getCurrency(),
                    o.getDueOn(), Status.PAID, p.getResponsibleId(), p.getCategory(), o.getPaidOn(), o.getPaidBy(),
                    o.getAmountPaid(), o.getNote(), o.getReceiptFileId());
        }

        static OccurrenceView unpaid(Payment p, LocalDate dueOn, LocalDate today) {
            return new OccurrenceView(null, p.getId(), p.getName(), p.getAmount(), p.getCurrency(), dueOn,
                    dueOn.isBefore(today) ? Status.OVERDUE : Status.PENDING, p.getResponsibleId(), p.getCategory(),
                    null, null, null, null, null);
        }
    }

    /** Vencimientos en un rango, ordenados por fecha. */
    public record OccurrenceRange(LocalDate from, LocalDate to, LocalDate today, List<OccurrenceView> occurrences) {
    }
}

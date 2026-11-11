package com.famy.payment.application;

import com.famy.family.FamilyMembership;
import com.famy.notification.NotificationType;
import com.famy.notification.Reminder;
import com.famy.notification.ReminderSource;
import com.famy.payment.domain.Payment;
import com.famy.payment.domain.PaymentOccurrence;
import com.famy.payment.infrastructure.PaymentOccurrenceRepository;
import com.famy.payment.infrastructure.PaymentRepository;
import com.famy.shared.time.Money;
import com.famy.shared.time.Spanish;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-8.3: aviso N días antes y el día del vencimiento (a las 9:00 de la familia) al responsable o, si no hay, a los
 * adultos. No se avisa de lo que ya está pagado.
 */
@Component
class PaymentReminders implements ReminderSource {

    private final PaymentRepository payments;
    private final PaymentOccurrenceRepository paid;
    private final FamilyMembership membership;

    PaymentReminders(PaymentRepository payments, PaymentOccurrenceRepository paid, FamilyMembership membership) {
        this.payments = payments;
        this.paid = paid;
        this.membership = membership;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reminder> remindersBetween(Instant from, Instant to) {
        List<Reminder> out = new ArrayList<>();
        Map<UUID, ZoneId> zones = new HashMap<>();
        for (Payment p : payments.findAllActive()) {
            ZoneId zone = zones.computeIfAbsent(p.getFamilyId(), membership::timezone);
            LocalDate fromDay = from.atZone(zone).toLocalDate().minusDays(1);
            LocalDate toDay = to.atZone(zone).toLocalDate().plusDays(p.getRemindDaysBefore() + 1L);
            for (LocalDate due : PaymentService.dueDates(p, fromDay, toDay, 100)) {
                for (int daysBefore : p.getRemindDaysBefore() > 0
                        ? List.of(p.getRemindDaysBefore(), 0) : List.of(0)) {
                    Instant fireAt = due.minusDays(daysBefore).atTime(Reminder.DAILY_HOUR).atZone(zone).toInstant();
                    if (fireAt.isAfter(from) && !fireAt.isAfter(to)
                            && paid.find(p.getId(), due).map(PaymentOccurrence::getId).isEmpty()) {
                        out.add(reminder(p, due, daysBefore, fireAt, zone));
                    }
                }
            }
        }
        return out;
    }

    private Reminder reminder(Payment p, LocalDate due, int daysBefore, Instant fireAt, ZoneId zone) {
        Set<UUID> adults = membership.activeAdultIds(p.getFamilyId());
        Set<UUID> recipients = p.getResponsibleId() != null && adults.contains(p.getResponsibleId())
                ? Set.of(p.getResponsibleId()) : adults;
        LocalDate today = fireAt.atZone(zone).toLocalDate();
        String when = daysBefore == 0 ? "Vence hoy"
                : daysBefore == 1 ? "Vence mañana"
                : "Vence en %d días (%s)".formatted(daysBefore, Spanish.day(due, today));
        return new Reminder("payment:%s:%s:%d".formatted(p.getId(), due, daysBefore), p.getFamilyId(), recipients,
                NotificationType.PAYMENT, "Pago: " + p.getName(),
                when + " · " + Money.format(p.getAmount(), p.getCurrency()), "/pagos", fireAt);
    }
}

package com.famy.celebration.application;

import com.famy.celebration.domain.Celebration;
import com.famy.celebration.domain.Celebration.Type;
import com.famy.celebration.infrastructure.CelebrationRepository;
import com.famy.family.FamilyMembership;
import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.notification.NotificationType;
import com.famy.notification.Reminder;
import com.famy.notification.ReminderSource;
import com.famy.shared.time.Spanish;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-9.3: avisos 7 días antes, 1 día antes y el mismo día (a las 9:00 de la familia) a todos los miembros excepto la
 * persona festejada.
 */
@Component
class CelebrationReminders implements ReminderSource {

    static final List<Integer> DAYS_BEFORE = List.of(7, 1, 0);

    private final CelebrationRepository celebrations;
    private final FamilyMembership membership;
    private final UserDirectory users;

    CelebrationReminders(CelebrationRepository celebrations, FamilyMembership membership, UserDirectory users) {
        this.celebrations = celebrations;
        this.membership = membership;
        this.users = users;
    }

    private record Dated(String key, UUID familyId, Type type, String name, UUID userId, int month, int day,
            Integer year) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reminder> remindersBetween(Instant from, Instant to) {
        Map<UUID, List<Celebration>> rows = celebrations.findAllActive().stream()
                .collect(Collectors.groupingBy(Celebration::getFamilyId));
        List<Reminder> out = new ArrayList<>();
        for (UUID familyId : membership.activeFamilyIds()) {
            ZoneId zone = membership.timezone(familyId);
            Set<UUID> members = membership.activeMemberIds(familyId);
            Map<UUID, UserSummary> people = users.findAll(members);
            List<Dated> dates = new ArrayList<>();
            Set<UUID> registered = new HashSet<>();
            for (Celebration c : rows.getOrDefault(familyId, List.of())) {
                if (c.getUserId() == null) {
                    dates.add(new Dated(c.getId().toString(), familyId, c.getType(), c.getPersonName(), null,
                            c.getMonth(), c.getDay(), c.getYear()));
                } else {
                    registered.add(c.getUserId());
                }
            }
            // Cumpleaños de miembros: siempre desde su perfil (tengan o no registro con ideas de regalo).
            for (UUID m : members) {
                UserSummary u = people.get(m);
                if (u != null && u.birthday() != null) {
                    dates.add(new Dated("member-" + m, familyId, Type.BIRTHDAY, u.displayName(), m,
                            u.birthday().getMonthValue(), u.birthday().getDayOfMonth(), u.birthday().getYear()));
                }
            }
            for (Dated d : dates) {
                for (int before : DAYS_BEFORE) {
                    // La fecha que corresponde a cada aviso: la del día de envío + `before` días.
                    LocalDate first = from.atZone(zone).toLocalDate();
                    LocalDate last = to.atZone(zone).toLocalDate();
                    for (LocalDate sendDay = first; !sendDay.isAfter(last); sendDay = sendDay.plusDays(1)) {
                        LocalDate target = sendDay.plusDays(before);
                        if (!AnnualDates.in(target.getYear(), d.month(), d.day()).equals(target)) {
                            continue;
                        }
                        Instant fireAt = sendDay.atTime(Reminder.DAILY_HOUR).atZone(zone).toInstant();
                        if (fireAt.isAfter(from) && !fireAt.isAfter(to)) {
                            Set<UUID> recipients = new HashSet<>(members);
                            if (d.userId() != null) {
                                recipients.remove(d.userId());
                            }
                            if (!recipients.isEmpty()) {
                                out.add(reminder(d, target, before, recipients, fireAt));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Reminder reminder(Dated d, LocalDate date, int before, Set<UUID> recipients, Instant fireAt) {
        Integer years = d.year() == null || d.year() >= date.getYear() ? null : date.getYear() - d.year();
        String title = switch (d.type()) {
            case BIRTHDAY -> "Cumpleaños de " + d.name();
            case ANNIVERSARY -> "Aniversario: " + d.name();
            case OTHER -> d.name();
        };
        String what = switch (d.type()) {
            case BIRTHDAY -> years == null ? "Cumpleaños" : "Cumple " + years;
            case ANNIVERSARY -> years == null ? "Aniversario" : years + (years == 1 ? " año" : " años");
            case OTHER -> "Fecha especial";
        };
        String when = before == 0 ? "¡Es hoy!"
                : before == 1 ? what + " mañana"
                : what + " en %d días (%s)".formatted(before, Spanish.day(date, date.minusDays(before)));
        String body = before == 0 ? what + " hoy 🎉" : when;
        return new Reminder("celebration:%s:%s:%d".formatted(d.key(), date, before), d.familyId(), recipients,
                NotificationType.CELEBRATION, title, body, "/cumpleanos", fireAt);
    }
}

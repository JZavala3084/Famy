package com.famy.notification.domain;

import com.famy.notification.NotificationType;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** RF-11.2: qué tipos no mandan push y horario de silencio (en la zona del usuario). */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference extends BaseEntity<UUID> {

    @Id
    private UUID userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(20)[]")
    private List<String> mutedTypes = new ArrayList<>();

    private LocalTime quietFrom;

    private LocalTime quietTo;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public void update(Set<NotificationType> muted, LocalTime quietFrom, LocalTime quietTo) {
        this.mutedTypes = new ArrayList<>(muted.stream().map(Enum::name).sorted().toList());
        this.quietFrom = quietFrom;
        this.quietTo = quietTo;
        this.updatedAt = Instant.now();
    }

    public Set<NotificationType> getMutedTypes() {
        Set<NotificationType> out = EnumSet.noneOf(NotificationType.class);
        for (String t : mutedTypes) {
            try {
                out.add(NotificationType.valueOf(t));
            } catch (IllegalArgumentException ignored) {
                // tipo retirado
            }
        }
        return out;
    }

    public LocalTime getQuietFrom() {
        return quietFrom;
    }

    public LocalTime getQuietTo() {
        return quietTo;
    }

    /** ¿{@code time} cae en el horario de silencio? Puede cruzar la medianoche (22:00 a 07:00). */
    public boolean isQuietAt(LocalTime time) {
        if (quietFrom == null || quietTo == null || quietFrom.equals(quietTo)) {
            return false;
        }
        return quietFrom.isBefore(quietTo)
                ? !time.isBefore(quietFrom) && time.isBefore(quietTo)
                : !time.isBefore(quietFrom) || time.isBefore(quietTo);
    }

    @Override
    public UUID getId() {
        return userId;
    }
}

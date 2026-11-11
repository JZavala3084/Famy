package com.famy.celebration.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "celebration")
public class Celebration extends BaseEntity<UUID> {

    public enum Type {
        BIRTHDAY,
        ANNIVERSARY,
        OTHER
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<GiftIdea>> GIFTS = new TypeReference<>() {
    };

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String personName;

    private UUID userId;

    private Short month;

    private Short day;

    private Short year;

    private String notes;

    /** JSONB guardado como texto: se convierte aquí para no depender del mapeo JSON de Hibernate. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String giftIdeas = "[]";

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Celebration() {
    }

    /** Datos editables (reemplazo completo), ya validados. Para un miembro, la fecha va nula (sale del perfil). */
    public record CelebrationData(Type type, String personName, UUID userId, Integer month, Integer day, Integer year,
            String notes, List<GiftIdea> giftIdeas) {
    }

    public Celebration(UUID familyId, CelebrationData data, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.userId = data.userId();
        apply(data);
    }

    public void edit(CelebrationData data) {
        apply(data);
    }

    private void apply(CelebrationData d) {
        this.type = d.type();
        this.personName = blankToNull(d.personName());
        this.month = d.month() == null ? null : d.month().shortValue();
        this.day = d.day() == null ? null : d.day().shortValue();
        this.year = d.year() == null ? null : d.year().shortValue();
        this.notes = blankToNull(d.notes());
        this.giftIdeas = JSON.writeValueAsString(d.giftIdeas());
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.deletedAt = Instant.now();
        this.updatedAt = deletedAt;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Type getType() {
        return type;
    }

    public String getPersonName() {
        return personName;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getMonth() {
        return month == null ? null : month.intValue();
    }

    public Integer getDay() {
        return day == null ? null : day.intValue();
    }

    public Integer getYear() {
        return year == null ? null : year.intValue();
    }

    public String getNotes() {
        return notes;
    }

    public List<GiftIdea> getGiftIdeas() {
        return JSON.readValue(giftIdeas, GIFTS);
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

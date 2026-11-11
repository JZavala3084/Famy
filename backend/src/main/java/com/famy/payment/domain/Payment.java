package com.famy.payment.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String rrule;

    @Column(nullable = false)
    private LocalDate firstDueOn;

    @Column(nullable = false)
    private int remindDaysBefore;

    private UUID responsibleId;

    private String category;

    private String notes;

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    /** Datos editables (reemplazo completo), ya validados. */
    public record PaymentData(String name, BigDecimal amount, String currency, String rrule, LocalDate firstDueOn,
            int remindDaysBefore, UUID responsibleId, String category, String notes) {
    }

    public Payment(UUID familyId, PaymentData data, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        apply(data);
    }

    public void edit(PaymentData data) {
        apply(data);
    }

    private void apply(PaymentData d) {
        this.name = d.name().strip();
        this.amount = d.amount();
        this.currency = d.currency();
        this.rrule = d.rrule();
        this.firstDueOn = d.firstDueOn();
        this.remindDaysBefore = d.remindDaysBefore();
        this.responsibleId = d.responsibleId();
        this.category = blankToNull(d.category());
        this.notes = blankToNull(d.notes());
        touch();
    }

    public void delete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRrule() {
        return rrule;
    }

    public LocalDate getFirstDueOn() {
        return firstDueOn;
    }

    public int getRemindDaysBefore() {
        return remindDaysBefore;
    }

    public UUID getResponsibleId() {
        return responsibleId;
    }

    public String getCategory() {
        return category;
    }

    public String getNotes() {
        return notes;
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

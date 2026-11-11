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

/** Un vencimiento pagado. Los pendientes y vencidos no se guardan: salen de la regla del pago y la fecha. */
@Entity
@Table(name = "payment_occurrence")
public class PaymentOccurrence extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false)
    private UUID paidBy;

    @Column(nullable = false)
    private BigDecimal amountPaid;

    private String note;

    private UUID receiptFileId;

    private UUID transactionId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentOccurrence() {
    }

    public PaymentOccurrence(Payment payment, LocalDate dueOn, LocalDate paidOn, UUID paidBy, BigDecimal amountPaid,
            String note) {
        this.id = Ids.newId();
        this.paymentId = payment.getId();
        this.familyId = payment.getFamilyId();
        this.dueOn = dueOn;
        this.status = "PAID";
        this.paidOn = paidOn;
        this.paidBy = paidBy;
        this.amountPaid = amountPaid;
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void attachReceipt(UUID fileId) {
        this.receiptFileId = fileId;
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public String getNote() {
        return note;
    }

    public UUID getReceiptFileId() {
        return receiptFileId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

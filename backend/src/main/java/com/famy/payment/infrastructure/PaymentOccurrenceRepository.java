package com.famy.payment.infrastructure;

import com.famy.payment.domain.PaymentOccurrence;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentOccurrenceRepository extends JpaRepository<PaymentOccurrence, UUID> {

    @Query("select o from PaymentOccurrence o where o.paymentId = :paymentId and o.dueOn = :dueOn")
    Optional<PaymentOccurrence> find(UUID paymentId, LocalDate dueOn);

    @Query("""
            select o from PaymentOccurrence o where o.familyId = :familyId
            and o.dueOn >= :from and o.dueOn <= :to""")
    List<PaymentOccurrence> findByFamilyBetween(UUID familyId, LocalDate from, LocalDate to);

    @Query("select o from PaymentOccurrence o where o.paymentId in :paymentIds and o.dueOn >= :from and o.dueOn <= :to")
    List<PaymentOccurrence> findByPaymentsBetween(Collection<UUID> paymentIds, LocalDate from, LocalDate to);
}

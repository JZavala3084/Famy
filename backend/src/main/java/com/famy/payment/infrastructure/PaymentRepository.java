package com.famy.payment.infrastructure;

import com.famy.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("select p from Payment p where p.id = :id and p.familyId = :familyId and p.deletedAt is null")
    Optional<Payment> findActive(UUID familyId, UUID id);

    @Query("select p from Payment p where p.familyId = :familyId and p.deletedAt is null order by p.name")
    List<Payment> findByFamily(UUID familyId);

    @Query("select p from Payment p where p.deletedAt is null")
    List<Payment> findAllActive();

    @Query("select count(p) from Payment p where p.familyId = :familyId and p.deletedAt is null")
    long countActive(UUID familyId);
}

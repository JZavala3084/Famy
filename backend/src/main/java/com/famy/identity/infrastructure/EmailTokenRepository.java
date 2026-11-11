package com.famy.identity.infrastructure;

import com.famy.identity.domain.EmailToken;
import com.famy.identity.domain.EmailToken.Purpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);

    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateAll(UUID userId, Purpose purpose, Instant now);

    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}

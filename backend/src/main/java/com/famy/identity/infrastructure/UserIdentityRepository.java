package com.famy.identity.infrastructure;

import com.famy.identity.domain.UserIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    List<UserIdentity> findByUserId(UUID userId);
}

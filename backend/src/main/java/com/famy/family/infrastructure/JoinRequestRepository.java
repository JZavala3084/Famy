package com.famy.family.infrastructure;

import com.famy.family.domain.JoinRequest;
import com.famy.family.domain.JoinRequest.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    List<JoinRequest> findByFamilyIdAndStatusOrderByCreatedAt(UUID familyId, Status status);

    Optional<JoinRequest> findByFamilyIdAndUserIdAndStatus(UUID familyId, UUID userId, Status status);

    List<JoinRequest> findByUserIdAndStatus(UUID userId, Status status);

    Optional<JoinRequest> findByIdAndFamilyId(UUID id, UUID familyId);
}

package com.famy.family.infrastructure;

import com.famy.family.domain.Family;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, UUID> {
}

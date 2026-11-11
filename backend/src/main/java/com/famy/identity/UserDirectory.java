package com.famy.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consulta de usuarios para otros módulos (sin exponer las entidades de identity). */
public interface UserDirectory {

    Optional<UserSummary> find(UUID userId);

    Map<UUID, UserSummary> findAll(Collection<UUID> userIds);
}

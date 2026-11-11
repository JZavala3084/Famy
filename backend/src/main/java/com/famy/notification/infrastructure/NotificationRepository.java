package com.famy.notification.infrastructure;

import com.famy.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.userId = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findByUser(UUID userId, Limit limit);

    @Query("select n from Notification n where n.id = :id and n.userId = :userId")
    Optional<Notification> findOwn(UUID userId, UUID id);

    @Query("select count(n) from Notification n where n.userId = :userId and n.readAt is null")
    long countUnread(UUID userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(UUID userId, Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(Instant before);
}

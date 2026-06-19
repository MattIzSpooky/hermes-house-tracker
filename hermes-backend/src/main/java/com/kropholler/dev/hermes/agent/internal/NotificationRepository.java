package com.kropholler.dev.hermes.agent.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop50ByClientIdOrderByCreatedAtDesc(UUID clientId);
    long countByClientIdAndReadFalse(UUID clientId);
}

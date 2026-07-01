package com.kropholler.dev.hermes.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByClientIdOrderByCreatedAtDesc(UUID clientId);
    long countByClientIdAndReadFalse(UUID clientId);
}

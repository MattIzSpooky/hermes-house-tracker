package com.kropholler.dev.hermes.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadFalse(UUID userId);

    List<NotificationEntity> findByEncryptionKeyVersionLessThan(int version, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.title = :title, n.body = :body, n.encryptionKeyVersion = :version WHERE n.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("title") String title, @Param("body") String body, @Param("version") int version);
}

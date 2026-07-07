package com.kropholler.dev.hermes.ai.agent.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {
    List<AgentTaskEntity> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTaskEntity> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AgentTaskStatus status);

    List<AgentTaskEntity> findByEncryptionKeyVersionLessThan(int version, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgentTaskEntity t SET t.name = :name, t.payload = :payload, t.encryptionKeyVersion = :version WHERE t.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("name") String name, @Param("payload") String payload, @Param("version") int version);
}

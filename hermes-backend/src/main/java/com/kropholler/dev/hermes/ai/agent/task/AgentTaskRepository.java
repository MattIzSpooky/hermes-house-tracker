package com.kropholler.dev.hermes.ai.agent.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {
    List<AgentTaskEntity> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTaskEntity> findAllByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, AgentTaskStatus status);
}

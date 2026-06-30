package com.kropholler.dev.hermes.ai.agent.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTask> findAllByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, AgentTaskStatus status);
}

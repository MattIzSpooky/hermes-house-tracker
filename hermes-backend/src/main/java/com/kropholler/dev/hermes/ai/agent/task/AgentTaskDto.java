package com.kropholler.dev.hermes.ai.agent.task;

import java.time.Instant;
import java.util.UUID;

public record AgentTaskDto(
    UUID id,
    AgentTaskType type,
    AgentTaskStatus status,
    UUID clientId,
    String name,
    String schedule,
    Instant lastRunAt,
    Instant nextRunAt,
    Instant createdAt
) {}

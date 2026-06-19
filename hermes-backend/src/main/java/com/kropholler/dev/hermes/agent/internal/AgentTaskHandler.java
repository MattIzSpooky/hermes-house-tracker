package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskType;

import java.util.Optional;

public interface AgentTaskHandler {
    AgentTaskType getType();
    Optional<NotificationContent> handle(AgentTask task);
}

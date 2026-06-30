package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.kropholler.dev.hermes.ai.agent.task.AgentTask;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.notification.NotificationContent;

import java.util.Optional;

public interface AgentTaskHandler {
    AgentTaskType getType();
    Optional<NotificationContent> handle(AgentTask task);
}

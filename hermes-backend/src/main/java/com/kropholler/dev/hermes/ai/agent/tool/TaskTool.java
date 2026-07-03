package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
    }
}

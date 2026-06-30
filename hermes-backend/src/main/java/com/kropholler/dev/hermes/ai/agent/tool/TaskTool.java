package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID clientId;
    protected final AgentTaskService agentTaskService;

    protected TaskTool(UUID clientId, AgentTaskService agentTaskService) {
        this.clientId = clientId;
        this.agentTaskService = agentTaskService;
    }
}

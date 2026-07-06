package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;
    protected final String email;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService, String email) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
        this.email = email;
    }

    /** True only if the live JWT email for this request was present and non-blank. */
    protected boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}

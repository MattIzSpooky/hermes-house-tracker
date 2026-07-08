package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@Slf4j
class TriggerResearchTool extends TaskTool {

    protected TriggerResearchTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }

    @Tool(description = "Queue a background research task. "
        + "Call this when the user wants a deep analysis, a full market report, or asks to 'research' something. "
        + "The AI will use all available tools to answer the prompt and deliver results as a notification. "
        + "Do NOT run research inline in chat — always use this tool.")
    public String triggerResearch(
        @ToolParam(description = "The research question or task to investigate in detail") String prompt
    ) {
        log.info("triggerResearch called: userId={}, promptLength={}", userId, prompt != null ? prompt.length() : 0);
        if (!hasEmail()) {
            log.warn("triggerResearch rejected for user {}: no email on file", userId);
            return "Please make sure your account has an email address before setting up notifications.";
        }
        agentTaskService.createResearch(userId, prompt);
        log.info("Research task queued for user {}", userId);
        return "Research queued — results will appear as a notification shortly.";
    }
}

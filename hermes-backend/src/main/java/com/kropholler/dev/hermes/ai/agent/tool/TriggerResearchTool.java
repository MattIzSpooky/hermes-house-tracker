package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@RequiredArgsConstructor
public class TriggerResearchTool {

    private final UUID clientId;
    private final AgentTaskService agentTaskService;

    @Tool(description = "Queue a background research task. "
        + "Call this when the user wants a deep analysis, a full market report, or asks to 'research' something. "
        + "The AI will use all available tools to answer the prompt and deliver results as a notification. "
        + "Do NOT run research inline in chat — always use this tool.")
    public String triggerResearch(
        @ToolParam(description = "The research question or task to investigate in detail") String prompt
    ) {
        agentTaskService.createResearch(clientId, prompt);
        return "Research queued — results will appear as a notification shortly.";
    }
}

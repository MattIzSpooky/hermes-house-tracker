package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

class TriggerDigestTool extends TaskTool {

    protected TriggerDigestTool(UUID clientId, AgentTaskService agentTaskService) {
        super(clientId, agentTaskService);
    }

    @Tool(description = "Schedule a weekly market digest for one or more cities. "
        + "Call this when the user asks for a weekly summary, market digest, or recurring market update for specific cities. "
        + "The digest runs every Monday at 08:00 and delivers an AI-narrated market summary as a notification.")
    public String triggerDigest(
        @ToolParam(description = "Comma-separated list of cities to include in the digest, e.g. 'Amsterdam,Utrecht'") String cities,
        @ToolParam(description = "A short name for this digest, e.g. 'Weekly Amsterdam & Utrecht digest'") String name
    ) {
        List<String> cityList = Arrays.stream(cities.split(","))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
        agentTaskService.createDigest(clientId, name, cityList);
        return "Weekly digest scheduled for " + cities + " — I'll send you a market summary every Monday morning.";
    }
}

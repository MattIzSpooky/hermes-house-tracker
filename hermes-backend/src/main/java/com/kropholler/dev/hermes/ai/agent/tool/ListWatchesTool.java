package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ListWatchesTool {

    private final UUID clientId;
    private final AgentTaskService agentTaskService;

    @Tool(description = "List the user's active watches (listing alerts). "
        + "Call this when the user asks what alerts or watches they have set up. "
        + "Optionally cancel a watch by providing its ID.")
    public String listWatches(
        @ToolParam(required = false, description = "ID of the watch to cancel. Omit to just list watches.") UUID cancelId
    ) {
        if (cancelId != null) {
            agentTaskService.delete(cancelId);
            return "Watch " + cancelId + " cancelled.";
        }

        List<AgentTaskDto> tasks = agentTaskService.findByClientId(clientId);
        if (tasks.isEmpty()) {
            return "You have no active watches. Ask me to set one up — for example: 'Alert me when a 3-bed house in Utrecht appears under €400,000.'";
        }

        StringBuilder sb = new StringBuilder("Your active watches:\n\n");
        for (AgentTaskDto t : tasks) {
            sb.append("- **").append(t.name()).append("** (ID: ").append(t.id()).append(")")
              .append(" — runs ").append(t.schedule() != null ? "daily at 08:00" : "once")
              .append(t.lastRunAt() != null ? ", last ran " + t.lastRunAt().toString().substring(0, 10) : "")
              .append("\n");
        }
        return sb.toString();
    }
}

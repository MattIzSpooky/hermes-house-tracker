package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.ai.ChatToolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Contributes the three agent-task tools (save watch, trigger research, list watches)
 * to the AI chat pipeline. Lives in the {@code agent} module so it can access
 * {@link AgentTaskService} without creating a circular dependency — {@code ai}
 * depends on the {@link ChatToolProvider} interface it owns; {@code agent} depends
 * on {@code ai} (already established by the task-handler implementations).
 */
@Component
@RequiredArgsConstructor
public class AgentChatToolProvider implements ChatToolProvider {

    private final AgentTaskService agentTaskService;

    @Override
    public List<Object> provideTools(UUID clientId) {
        return List.of(
            new SaveWatchTool(clientId, agentTaskService),
            new TriggerResearchTool(clientId, agentTaskService),
            new ListWatchesTool(clientId, agentTaskService)
        );
    }
}

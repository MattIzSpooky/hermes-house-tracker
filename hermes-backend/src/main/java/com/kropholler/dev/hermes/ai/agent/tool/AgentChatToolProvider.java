package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.ChatToolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentChatToolProvider implements ChatToolProvider {

    private final AgentTaskService agentTaskService;

    @Override
    public List<Object> provideTools(UUID clientId) {
        return List.of(
            new SaveWatchTool(clientId, agentTaskService),
            new TriggerResearchTool(clientId, agentTaskService),
            new TriggerDigestTool(clientId, agentTaskService),
            new ListWatchesTool(clientId, agentTaskService)
        );
    }
}

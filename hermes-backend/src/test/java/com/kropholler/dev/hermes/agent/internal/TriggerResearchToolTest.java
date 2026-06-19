package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskDto;
import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerResearchToolTest {

    @Test
    void queuesResearchTask() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.RESEARCH,
            AgentTaskStatus.ACTIVE, clientId, "Research: market trends", null, null, Instant.now(), Instant.now());
        when(agentTaskService.createResearch(any(), anyString())).thenReturn(dto);

        TriggerResearchTool tool = new TriggerResearchTool(clientId, agentTaskService);
        String result = tool.triggerResearch("What are the current market trends in Amsterdam?");

        verify(agentTaskService).createResearch(clientId, "What are the current market trends in Amsterdam?");
        assertThat(result).contains("queued");
    }
}

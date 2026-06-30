package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.tool.ListWatchesTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListWatchesToolTest {

    @Test
    void returnsFormattedActiveWatches() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        UUID watchId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(watchId, AgentTaskType.WATCH,
            AgentTaskStatus.ACTIVE, clientId, "Utrecht 3-bed", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.findByClientId(clientId)).thenReturn(List.of(dto));

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(null);

        verify(agentTaskService).findByClientId(clientId);
        assertThat(result).contains("Utrecht 3-bed");
        assertThat(result).contains(watchId.toString());
    }

    @Test
    void returnsEmptyMessageWhenNoWatches() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.findByClientId(clientId)).thenReturn(List.of());

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(null);

        assertThat(result).contains("no active watches");
    }

    @Test
    void cancelWatchWhenCancelIdProvided() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        UUID cancelId = UUID.randomUUID();

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(cancelId);

        verify(agentTaskService).delete(cancelId);
        assertThat(result).contains("cancelled");
        assertThat(result).contains(cancelId.toString());
    }
}

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
        when(agentTaskService.findByUserId(clientId)).thenReturn(List.of(dto));

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(null);

        verify(agentTaskService).findByUserId(clientId);
        assertThat(result).contains("Utrecht 3-bed");
        assertThat(result).contains(watchId.toString());
    }

    @Test
    void returnsEmptyMessageWhenNoWatches() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.findByUserId(clientId)).thenReturn(List.of());

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

        verify(agentTaskService).delete(cancelId, clientId);
        assertThat(result).contains("cancelled");
        assertThat(result).contains(cancelId.toString());
    }

    @Test
    void returnsFormattedWatchWithNullScheduleAndNonNullLastRunAt() {
        // Covers L36 false (schedule==null → "once") and L37 true (lastRunAt!=null → "last ran ...")
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        UUID watchId = UUID.randomUUID();
        Instant lastRun = Instant.parse("2026-06-01T08:00:00Z");
        AgentTaskDto dto = new AgentTaskDto(watchId, AgentTaskType.WATCH,
            AgentTaskStatus.ACTIVE, clientId, "One-off watch", null, lastRun, Instant.now(), Instant.now());
        when(agentTaskService.findByUserId(clientId)).thenReturn(List.of(dto));

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(null);

        assertThat(result).contains("once");
        assertThat(result).contains("last ran 2026-06-01");
    }
}

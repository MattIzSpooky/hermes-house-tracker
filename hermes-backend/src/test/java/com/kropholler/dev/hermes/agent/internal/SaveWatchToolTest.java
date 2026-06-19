package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskDto;
import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveWatchToolTest {

    @Test
    void createsWatchWithExtractedCriteria() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.WATCH,
            AgentTaskStatus.ACTIVE, clientId, "Utrecht 3-bed", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createWatch(any(), anyString(), any())).thenReturn(dto);

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService);
        String result = tool.saveWatch("Utrecht 3-bed", "Utrecht", null, null, 400000, 3, null, null, null, null, null);

        ArgumentCaptor<WatchPayload> cap = ArgumentCaptor.forClass(WatchPayload.class);
        verify(agentTaskService).createWatch(eq(clientId), eq("Utrecht 3-bed"), cap.capture());
        assertThat(cap.getValue().city()).isEqualTo("Utrecht");
        assertThat(cap.getValue().maxPrice()).isEqualTo(400000);
        assertThat(result).contains("Utrecht 3-bed").contains("saved");
    }
}

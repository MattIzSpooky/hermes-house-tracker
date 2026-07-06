package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerDigestToolTest {

    @Test
    void triggerDigest_delegatesToServiceAndReturnsConfirmation() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.DIGEST,
            AgentTaskStatus.ACTIVE, clientId, "Weekly digest", "0 8 * * MON", null, Instant.now(), Instant.now());
        when(agentTaskService.createDigest(any(), anyString(), anyList())).thenReturn(dto);

        TriggerDigestTool tool = new TriggerDigestTool(clientId, agentTaskService, "user@hermes.local");
        String result = tool.triggerDigest("Amsterdam,Utrecht", "Weekly digest");

        verify(agentTaskService).createDigest(clientId, "Weekly digest", List.of("Amsterdam", "Utrecht"));
        assertThat(result).contains("Weekly digest scheduled");
        assertThat(result).contains("Amsterdam,Utrecht");
    }

    @Test
    void triggerDigest_blankEntryInCitiesString_filteredOut() {
        // Covers L27 filter false branch: blank entry after split+strip is removed
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.createDigest(any(), anyString(), anyList())).thenReturn(null);

        TriggerDigestTool tool = new TriggerDigestTool(clientId, agentTaskService, "user@hermes.local");
        tool.triggerDigest("Amsterdam, ,Utrecht", "Digest");

        verify(agentTaskService).createDigest(clientId, "Digest", List.of("Amsterdam", "Utrecht"));
    }
}

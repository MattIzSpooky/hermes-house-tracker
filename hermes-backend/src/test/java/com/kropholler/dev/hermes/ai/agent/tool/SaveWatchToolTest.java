package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.ai.agent.tool.SaveWatchTool;
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

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, "user@hermes.local");
        String result = tool.saveWatch("Utrecht 3-bed", "Utrecht", null, null, 400000, 3, null, null, null, null, null);

        ArgumentCaptor<WatchPayload> cap = ArgumentCaptor.forClass(WatchPayload.class);
        verify(agentTaskService).createWatch(eq(clientId), eq("Utrecht 3-bed"), cap.capture());
        assertThat(cap.getValue().city()).isEqualTo("Utrecht");
        assertThat(cap.getValue().maxPrice()).isEqualTo(400000);
        assertThat(result).contains("Utrecht 3-bed").contains("saved");
    }

    @Test
    void saveWatch_nullName_buildNameFromCityBedroomsAndPrice() {
        // Covers L33 first false (name==null → buildName), L45/46/47 true, L48 false (sb non-blank)
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.createWatch(any(), anyString(), any())).thenReturn(null);

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, "user@hermes.local");
        String result = tool.saveWatch(null, "Amsterdam", null, null, 300000, 3, null, null, null, null, null);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentTaskService).createWatch(eq(clientId), nameCaptor.capture(), any());
        assertThat(nameCaptor.getValue()).contains("Amsterdam").contains("3-bed").contains("300");
        assertThat(result).contains("saved");
    }

    @Test
    void saveWatch_blankName_noFields_nameBecomesNewWatch() {
        // Covers L33 second false (name blank → buildName), L45/46/47 false, L48 true (sb blank → "New watch")
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.createWatch(any(), anyString(), any())).thenReturn(null);

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, "user@hermes.local");
        String result = tool.saveWatch("  ", null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentTaskService).createWatch(eq(clientId), nameCaptor.capture(), any());
        assertThat(nameCaptor.getValue()).isEqualTo("New watch");
        assertThat(result).contains("New watch");
    }

    @Test
    void saveWatch_blankCityString_treatedAsNullInPayload() {
        // Covers L52 blankToNull branch: s != null && s.isBlank() → return null
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.createWatch(any(), anyString(), any())).thenReturn(null);

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, "user@hermes.local");
        tool.saveWatch("My watch", "  ", "  ", null, null, null, null, null, "  ", "  ", null);

        ArgumentCaptor<WatchPayload> cap = ArgumentCaptor.forClass(WatchPayload.class);
        verify(agentTaskService).createWatch(eq(clientId), eq("My watch"), cap.capture());
        assertThat(cap.getValue().city()).isNull();
        assertThat(cap.getValue().province()).isNull();
    }

    @Test
    void saveWatch_noEmail_rejectsWithoutCreatingTask() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, null);
        String result = tool.saveWatch("Utrecht 3-bed", "Utrecht", null, null, 400000, 3, null, null, null, null, null);

        assertThat(result).contains("email address");
        verify(agentTaskService, never()).createWatch(any(), anyString(), any());
    }
}

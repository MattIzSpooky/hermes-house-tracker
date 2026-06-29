package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTaskController.class)
class AgentTaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AgentTaskService agentTaskService;

    @Test
    void getAgentTasks_returnsMappedList() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(taskId, AgentTaskType.WATCH, AgentTaskStatus.ACTIVE,
            clientId, "My watch", "0 0 8 * * *", Instant.parse("2026-06-01T08:00:00Z"),
            Instant.parse("2026-06-20T08:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));

        when(agentTaskService.findByClientId(clientId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/agent-tasks").param("clientId", clientId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(taskId.toString()))
            .andExpect(jsonPath("$[0].type").value("WATCH"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].name").value("My watch"))
            .andExpect(jsonPath("$[0].schedule").value("0 0 8 * * *"));
    }

    @Test
    void getAgentTasks_emptyList_returnsEmptyArray() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(agentTaskService.findByClientId(clientId)).thenReturn(List.of());

        mockMvc.perform(get("/api/agent-tasks").param("clientId", clientId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteAgentTask_callsServiceAndReturns204() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(taskId);
    }
}

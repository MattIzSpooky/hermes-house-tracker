package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTaskController.class)
@Import(SecurityConfig.class)
class AgentTaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean AgentTaskService agentTaskService;
    @MockitoBean AgentTaskApiMapper agentTaskApiMapper;

    @Test
    void getAgentTasks_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(taskId, AgentTaskType.WATCH, AgentTaskStatus.ACTIVE,
            userId, "My watch", "0 0 8 * * *", Instant.parse("2026-06-01T08:00:00Z"),
            Instant.parse("2026-06-20T08:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));

        AgentTaskResponse response = new AgentTaskResponse();
        response.setId(taskId);
        response.setType("WATCH");
        response.setStatus("ACTIVE");
        response.setName("My watch");
        response.setSchedule("0 0 8 * * *");

        when(agentTaskService.findByUserId(userId)).thenReturn(List.of(dto));
        when(agentTaskApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/agent-tasks")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(taskId.toString()))
            .andExpect(jsonPath("$[0].type").value("WATCH"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].name").value("My watch"))
            .andExpect(jsonPath("$[0].schedule").value("0 0 8 * * *"));

        verify(agentTaskService).findByUserId(eq(userId));
    }

    @Test
    void getAgentTasks_emptyList_returnsEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();
        when(agentTaskService.findByUserId(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/agent-tasks")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteAgentTask_usesSubjectFromJwt() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(eq(taskId), eq(callerId));
    }
}

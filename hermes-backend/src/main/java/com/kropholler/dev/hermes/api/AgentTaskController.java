package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.api.generated.AgentTasksApi;
import com.kropholler.dev.hermes.api.generated.model.AgentTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class AgentTaskController implements AgentTasksApi {

    private final AgentTaskService agentTaskService;

    @Override
    public ResponseEntity<List<AgentTaskResponse>> getAgentTasks(UUID clientId) {
        List<AgentTaskResponse> responses = agentTaskService.findByClientId(clientId)
            .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AgentTaskResponse toResponse(AgentTaskDto dto) {
        AgentTaskResponse r = new AgentTaskResponse();
        r.setId(dto.id());
        r.setType(dto.type() != null ? dto.type().name() : null);
        r.setStatus(dto.status() != null ? dto.status().name() : null);
        r.setClientId(dto.clientId());
        r.setName(dto.name());
        r.setSchedule(dto.schedule());
        r.setLastRunAt(dto.lastRunAt() != null ? dto.lastRunAt().atOffset(ZoneOffset.UTC) : null);
        r.setNextRunAt(dto.nextRunAt() != null ? dto.nextRunAt().atOffset(ZoneOffset.UTC) : null);
        r.setCreatedAt(dto.createdAt() != null ? dto.createdAt().atOffset(ZoneOffset.UTC) : null);
        return r;
    }
}

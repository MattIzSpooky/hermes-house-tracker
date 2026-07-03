package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTasksApi;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgentTaskController implements AgentTasksApi {

    private final AgentTaskService agentTaskService;
    private final AgentTaskApiMapper agentTaskApiMapper;

    @Override
    public ResponseEntity<List<AgentTaskResponse>> getAgentTasks() {
        List<AgentTaskResponse> responses = agentTaskService.findByUserId(CurrentUser.current().id())
            .stream().map(agentTaskApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

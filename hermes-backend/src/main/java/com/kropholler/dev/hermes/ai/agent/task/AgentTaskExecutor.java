package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.notification.NotificationService;
import com.kropholler.dev.hermes.ai.agent.task.handler.AgentTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
class AgentTaskExecutor {

    private final Map<AgentTaskType, AgentTaskHandler> handlers;
    private final AgentTaskService agentTaskService;
    private final NotificationService notificationService;

    AgentTaskExecutor(List<AgentTaskHandler> handlerList,
                              AgentTaskService agentTaskService,
                              NotificationService notificationService) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(AgentTaskHandler::getType, h -> h));
        this.agentTaskService = agentTaskService;
        this.notificationService = notificationService;
    }

    void execute(AgentTaskEntity task) {
        AgentTaskHandler handler = handlers.get(task.getType());
        log.info("Received task {} with type: {}", task.getId(), task.getType());
        if (handler == null) {
            log.warn("No handler registered for task type {}, skipping task {}", task.getType(), task.getId());
            return;
        }
        try {
            log.info("Executing task {} with type: {}", task.getId(), task.getType());

            handler.handle(task).ifPresent(content ->
                notificationService.save(task.getId(), task.getUserId(), content));
        } catch (Exception e) {
            log.error("Error executing task {}: {}", task.getId(), e.getMessage(), e);
        } finally {
            agentTaskService.markRan(task);
        }
    }
}

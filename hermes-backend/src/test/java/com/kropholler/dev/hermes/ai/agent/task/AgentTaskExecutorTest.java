package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.notification.NotificationService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskExecutor;
import com.kropholler.dev.hermes.ai.agent.task.handler.AgentTaskHandler;
import com.kropholler.dev.hermes.notification.NotificationContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTaskExecutorTest {

    @Mock AgentTaskService agentTaskService;
    @Mock NotificationService notificationService;

    AgentTaskHandler watchHandler;
    AgentTaskExecutor executor;

    @BeforeEach
    void setUp() {
        watchHandler = mock(AgentTaskHandler.class);
        when(watchHandler.getType()).thenReturn(AgentTaskType.WATCH);
        executor = new AgentTaskExecutor(List.of(watchHandler), agentTaskService, notificationService);
    }

    @Test
    void callsHandlerAndSavesNotificationWhenContentPresent() {
        AgentTaskEntity task = task(AgentTaskType.WATCH);
        NotificationContent content = new NotificationContent("title", "body", List.of());
        when(watchHandler.handle(task)).thenReturn(Optional.of(content));

        executor.execute(task);

        verify(notificationService).save(task.getId(), task.getUserId(), content);
        verify(agentTaskService).markRan(task);
    }

    @Test
    void doesNotSaveNotificationWhenHandlerReturnsEmpty() {
        AgentTaskEntity task = task(AgentTaskType.WATCH);
        when(watchHandler.handle(task)).thenReturn(Optional.empty());

        executor.execute(task);

        verify(notificationService, never()).save(any(), any(), any());
        verify(agentTaskService).markRan(task);
    }

    @Test
    void logsWarningForUnknownTaskType() {
        AgentTaskEntity task = task(AgentTaskType.DIGEST); // no handler registered

        executor.execute(task); // must not throw

        verify(agentTaskService, never()).markRan(any());
    }

    @Test
    void execute_handlerThrows_stillCallsMarkRan() {
        AgentTaskEntity task = task(AgentTaskType.WATCH);
        when(watchHandler.handle(task)).thenThrow(new RuntimeException("handler error"));

        executor.execute(task);

        verify(agentTaskService).markRan(task);
        verify(notificationService, never()).save(any(), any(), any());
    }

    private AgentTaskEntity task(AgentTaskType type) {
        AgentTaskEntity t = new AgentTaskEntity();
        t.setId(UUID.randomUUID());
        t.setType(type);
        t.setStatus(AgentTaskStatus.ACTIVE);
        t.setUserId(UUID.randomUUID());
        t.setName("test");
        t.setNextRunAt(Instant.now());
        return t;
    }
}

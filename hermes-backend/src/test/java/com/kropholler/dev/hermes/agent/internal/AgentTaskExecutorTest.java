package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.agent.NotificationService;
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
        AgentTask task = task(AgentTaskType.WATCH);
        NotificationContent content = new NotificationContent("title", "body", List.of());
        when(watchHandler.handle(task)).thenReturn(Optional.of(content));

        executor.execute(task);

        verify(notificationService).save(task.getId(), task.getClientId(), content);
        verify(agentTaskService).markRan(task);
    }

    @Test
    void doesNotSaveNotificationWhenHandlerReturnsEmpty() {
        AgentTask task = task(AgentTaskType.WATCH);
        when(watchHandler.handle(task)).thenReturn(Optional.empty());

        executor.execute(task);

        verify(notificationService, never()).save(any(), any(), any());
        verify(agentTaskService).markRan(task);
    }

    @Test
    void logsWarningForUnknownTaskType() {
        AgentTask task = task(AgentTaskType.DIGEST); // no handler registered

        executor.execute(task); // must not throw

        verify(agentTaskService, never()).markRan(any());
    }

    private AgentTask task(AgentTaskType type) {
        AgentTask t = new AgentTask();
        t.setId(UUID.randomUUID());
        t.setType(type);
        t.setStatus(AgentTaskStatus.ACTIVE);
        t.setClientId(UUID.randomUUID());
        t.setName("test");
        t.setNextRunAt(Instant.now());
        return t;
    }
}

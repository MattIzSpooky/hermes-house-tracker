package com.kropholler.dev.hermes.ai.agent.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTaskSchedulerTest {

    @Mock AgentTaskService agentTaskService;
    @Mock AgentTaskExecutor executor;
    @InjectMocks AgentTaskScheduler scheduler;

    @Test
    void tick_whenNothingDue_doesNotExecuteAny() {
        when(agentTaskService.findDueTasks()).thenReturn(List.of());

        scheduler.tick();

        verify(executor, never()).execute(any());
    }

    @Test
    void tick_whenTasksDue_executesAll() {
        AgentTaskEntity task = new AgentTaskEntity();
        when(agentTaskService.findDueTasks()).thenReturn(List.of(task));

        scheduler.tick();

        verify(executor).execute(task);
    }
}

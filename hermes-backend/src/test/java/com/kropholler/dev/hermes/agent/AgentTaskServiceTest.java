package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTask;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    @Mock AgentTaskRepository repo;
    @Spy ObjectMapper objectMapper;
    @InjectMocks
    AgentTaskService service;

    @Test
    void createWatchPersistsTaskWithDailySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WatchPayload payload = new WatchPayload("Utrecht", null, null, 400000, 3, null, null, null, null, null);
        service.createWatch(UUID.randomUUID(), "Utrecht 3-bed", payload);

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(repo).save(captor.capture());
        AgentTask saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.WATCH);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * *");
        assertThat(saved.getNextRunAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
    }

    @Test
    void createResearchSetsNullScheduleAndImmediateNextRun() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createResearch(UUID.randomUUID(), "analyse my favourites and recommend one");

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(repo).save(captor.capture());
        AgentTask saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.RESEARCH);
        assertThat(saved.getSchedule()).isNull();
        assertThat(saved.getNextRunAt()).isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void markRanSetsCompletedForOneShot() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTask task = new AgentTask();
        task.setSchedule(null);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.RESEARCH);
        task.setClientId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now());

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.COMPLETED);
        assertThat(task.getLastRunAt()).isNotNull();
    }

    @Test
    void markRanUpdatesNextRunAtForRepeating() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTask task = new AgentTask();
        task.setSchedule("0 0 8 * * *");
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.WATCH);
        task.setClientId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now().minusSeconds(60));

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
        assertThat(task.getNextRunAt()).isAfter(Instant.now());
    }
}

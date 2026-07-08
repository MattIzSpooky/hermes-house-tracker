package com.kropholler.dev.hermes.ai.agent.task;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    @Mock AgentTaskRepository repo;
    @Spy ObjectMapper objectMapper = new JsonMapper();
    @InjectMocks
    AgentTaskService service;

    @Test
    void createWatchPersistsTaskWithDailySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WatchPayload payload = new WatchPayload("Utrecht", null, null, 400000, 3, null, null, null, null, null);
        service.createWatch(UUID.randomUUID(), "Utrecht 3-bed", payload);

        ArgumentCaptor<AgentTaskEntity> captor = ArgumentCaptor.forClass(AgentTaskEntity.class);
        verify(repo).save(captor.capture());
        AgentTaskEntity saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.WATCH);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * *");
        assertThat(saved.getNextRunAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
    }

    @Test
    void createResearchSetsNullScheduleAndImmediateNextRun() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createResearch(UUID.randomUUID(), "analyse my favourites and recommend one");

        ArgumentCaptor<AgentTaskEntity> captor = ArgumentCaptor.forClass(AgentTaskEntity.class);
        verify(repo).save(captor.capture());
        AgentTaskEntity saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.RESEARCH);
        assertThat(saved.getSchedule()).isNull();
        assertThat(saved.getNextRunAt()).isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void markRanSetsCompletedForOneShot() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTaskEntity task = new AgentTaskEntity();
        task.setSchedule(null);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now());

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.COMPLETED);
        assertThat(task.getLastRunAt()).isNotNull();
    }

    @Test
    void markRanUpdatesNextRunAtForRepeating() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTaskEntity task = new AgentTaskEntity();
        task.setSchedule("0 0 8 * * *");
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.WATCH);
        task.setUserId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now().minusSeconds(60));

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
        assertThat(task.getNextRunAt()).isAfter(Instant.now());
    }

    @Test
    void createDigest_persistsTaskWithWeeklySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createDigest(UUID.randomUUID(), "Weekly digest", List.of("Amsterdam", "Utrecht"));

        ArgumentCaptor<AgentTaskEntity> captor = ArgumentCaptor.forClass(AgentTaskEntity.class);
        verify(repo).save(captor.capture());
        AgentTaskEntity saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.DIGEST);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * MON");
        assertThat(saved.getNextRunAt()).isNotNull();
    }

    @Test
    void createAreaResearch_persistsTaskWithDailySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AreaResearchPayload payload = new AreaResearchPayload(15, 10, 3, null, 80, null, 500000, null, null, null);
        service.createAreaResearch(UUID.randomUUID(), "Best nearby homes", payload);

        ArgumentCaptor<AgentTaskEntity> captor = ArgumentCaptor.forClass(AgentTaskEntity.class);
        verify(repo).save(captor.capture());
        AgentTaskEntity saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.AREA_RESEARCH);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * *");
        assertThat(saved.getNextRunAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
        assertThat(saved.getName()).isEqualTo("Best nearby homes");
    }

    @Test
    void findByUserId_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repo.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AgentTaskStatus.ACTIVE))
            .thenReturn(List.of());

        List<AgentTaskDto> result = service.findByUserId(userId);

        assertThat(result).isEmpty();
        verify(repo).findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AgentTaskStatus.ACTIVE);
    }

    @Test
    void delete_ownerDeletesSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(userId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        service.delete(taskId, userId);

        verify(repo).delete(task);
    }

    @Test
    void delete_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.findById(taskId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(taskId, userId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Agent task " + taskId + " not found");

        verify(repo, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void delete_throws403WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(ownerId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        assertThatThrownBy(() -> service.delete(taskId, callerId))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(repo, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void findOwned_ownerReturnsEntity() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(userId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        AgentTaskEntity result = service.findOwned(taskId, userId);

        assertThat(result).isSameAs(task);
    }

    @Test
    void findOwned_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.findById(taskId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findOwned(taskId, userId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Agent task " + taskId + " not found");
    }

    @Test
    void findOwned_throws403WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(ownerId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        assertThatThrownBy(() -> service.findOwned(taskId, callerId))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void findDueTasks_delegatesToRepository() {
        when(repo.findAllByStatusAndNextRunAtLessThanEqual(eq(AgentTaskStatus.ACTIVE), any(Instant.class)))
            .thenReturn(List.of());

        List<AgentTaskEntity> result = service.findDueTasks();

        assertThat(result).isEmpty();
    }

    @Test
    void serialize_throwsIllegalStateOnJsonException() throws Exception {
        doThrow(new tools.jackson.core.JacksonException("forced") {})
            .when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.createWatch(UUID.randomUUID(), "name",
                new WatchPayload(null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to serialize agent task payload");
    }
}

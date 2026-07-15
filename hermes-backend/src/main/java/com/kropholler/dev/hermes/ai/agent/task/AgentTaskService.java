package com.kropholler.dev.hermes.ai.agent.task;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.ResearchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.exception.ForbiddenException;
import com.kropholler.dev.hermes.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskRepository agentTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentTaskDto createWatch(UUID userId, String name, WatchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(payload));
        task.setSchedule("0 0 8 * * *");
        task.setNextRunAt(computeNext("0 0 8 * * *"));
        AgentTaskDto dto = toDto(agentTaskRepository.save(task));
        log.info("Created WATCH task {} for user {}", dto.id(), userId);
        return dto;
    }

    @Transactional
    public AgentTaskDto createResearch(UUID userId, String prompt) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(userId);
        task.setName("Research: " + prompt.substring(0, Math.min(60, prompt.length())));
        task.setPayload(serialize(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        AgentTaskDto dto = toDto(agentTaskRepository.save(task));
        log.info("Created RESEARCH task {} for user {}", dto.id(), userId);
        return dto;
    }

    @Transactional
    public AgentTaskDto createDigest(UUID userId, String name, List<String> cities) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.DIGEST);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(computeNext("0 0 8 * * MON"));
        AgentTaskDto dto = toDto(agentTaskRepository.save(task));
        log.info("Created DIGEST task {} for user {}, cities={}", dto.id(), userId, cities);
        return dto;
    }

    @Transactional
    public AgentTaskDto createAreaResearch(UUID userId, String name, AreaResearchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(payload));
        task.setSchedule("0 0 8 * * *");
        task.setNextRunAt(computeNext("0 0 8 * * *"));
        AgentTaskDto dto = toDto(agentTaskRepository.save(task));
        log.info("Created AREA_RESEARCH task {} for user {}", dto.id(), userId);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AgentTaskDto> findByUserId(UUID userId) {
        List<AgentTaskDto> tasks = agentTaskRepository
            .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AgentTaskStatus.ACTIVE)
            .stream().map(this::toDto).toList();
        log.debug("findByUserId found {} active task(s) for user {}", tasks.size(), userId);
        return tasks;
    }

    @Transactional
    public void delete(UUID taskId, UUID userId) {
        AgentTaskEntity task = findOwnedEntity(taskId, userId);
        agentTaskRepository.delete(task);
        log.info("Deleted task {} for user {}", taskId, userId);
    }

    @Transactional(readOnly = true)
    public AgentTaskEntity findOwned(UUID taskId, UUID userId) {
        return findOwnedEntity(taskId, userId);
    }

    private AgentTaskEntity findOwnedEntity(UUID taskId, UUID userId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Agent task " + taskId + " not found"));
        if (!task.getUserId().equals(userId)) {
            throw new ForbiddenException("Not authorized to access this agent task");
        }
        return task;
    }

    @Transactional
    public void markRan(AgentTaskEntity task) {
        task.setLastRunAt(Instant.now());
        if (task.getSchedule() != null) {
            task.setNextRunAt(computeNext(task.getSchedule()));
        } else {
            task.setStatus(AgentTaskStatus.COMPLETED);
        }
        agentTaskRepository.save(task);
        log.debug("markRan: task {} lastRunAt updated, nextRunAt={}, status={}",
            task.getId(), task.getNextRunAt(), task.getStatus());
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> findDueTasks() {
        List<AgentTaskEntity> due = agentTaskRepository.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());
        log.debug("findDueTasks found {} due task(s)", due.size());
        return due;
    }

    private Instant computeNext(String schedule) {
        CronExpression expr = CronExpression.parse(schedule);
        ZonedDateTime next = expr.next(ZonedDateTime.now());
        return next != null ? next.toInstant() : Instant.now().plus(1, ChronoUnit.DAYS);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize agent task payload", e);
        }
    }

    public AgentTaskDto toDto(AgentTaskEntity t) {
        return new AgentTaskDto(t.getId(), t.getType(), t.getStatus(), t.getUserId(),
            t.getName(), t.getSchedule(), t.getLastRunAt(), t.getNextRunAt(), t.getCreatedAt());
    }
}

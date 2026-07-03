package com.kropholler.dev.hermes.ai.agent.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.ResearchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskDto createResearch(UUID userId, String prompt) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(userId);
        task.setName("Research: " + prompt.substring(0, Math.min(60, prompt.length())));
        task.setPayload(serialize(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        return toDto(agentTaskRepository.save(task));
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
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<AgentTaskDto> findByUserId(UUID userId) {
        return agentTaskRepository
            .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AgentTaskStatus.ACTIVE)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(UUID taskId, UUID userId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Agent task " + taskId + " not found"));
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to delete this agent task");
        }
        agentTaskRepository.delete(task);
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
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> findDueTasks() {
        return agentTaskRepository.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());
    }

    private Instant computeNext(String schedule) {
        CronExpression expr = CronExpression.parse(schedule);
        ZonedDateTime next = expr.next(ZonedDateTime.now());
        return next != null ? next.toInstant() : Instant.now().plus(1, ChronoUnit.DAYS);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent task payload", e);
        }
    }

    public AgentTaskDto toDto(AgentTaskEntity t) {
        return new AgentTaskDto(t.getId(), t.getType(), t.getStatus(), t.getUserId(),
            t.getName(), t.getSchedule(), t.getLastRunAt(), t.getNextRunAt(), t.getCreatedAt());
    }
}

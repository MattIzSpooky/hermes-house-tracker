package com.kropholler.dev.hermes.ai.agent.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class AgentTaskScheduler {

    private final AgentTaskService agentTaskService;
    private final AgentTaskExecutor executor;

    @Scheduled(fixedDelay = 60_000)
    void tick() {
        List<AgentTaskEntity> due = agentTaskService.findDueTasks();
        if (!due.isEmpty()) {
            log.info("AgentTaskScheduler: executing {} due task(s)", due.size());
            due.forEach(executor::execute);
        }
    }
}

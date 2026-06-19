package com.kropholler.dev.hermes.agent;

import com.kropholler.dev.hermes.agent.internal.AgentTask;
import com.kropholler.dev.hermes.agent.internal.AgentTaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskScheduler {

    private final AgentTaskService agentTaskService;
    private final AgentTaskExecutor executor;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<AgentTask> due = agentTaskService.findDueTasks();
        if (!due.isEmpty()) {
            log.info("AgentTaskScheduler: executing {} due task(s)", due.size());
            due.forEach(executor::execute);
        }
    }
}

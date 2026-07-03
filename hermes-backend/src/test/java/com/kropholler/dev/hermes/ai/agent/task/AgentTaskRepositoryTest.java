package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AgentTaskRepositoryTest {

    @Autowired
    AgentTaskRepository repo;

    @Test
    void findsDueActiveTasks() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(UUID.randomUUID());
        task.setName("test watch");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().minusSeconds(60));
        repo.save(task);

        List<AgentTaskEntity> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getName()).isEqualTo("test watch");
    }

    @Test
    void doesNotReturnFutureTasks() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(UUID.randomUUID());
        task.setName("future task");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().plusSeconds(3600));
        repo.save(task);

        List<AgentTaskEntity> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).isEmpty();
    }
}

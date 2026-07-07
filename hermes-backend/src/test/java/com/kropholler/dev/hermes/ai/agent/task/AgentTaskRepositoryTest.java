package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class})
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AgentTaskRepositoryTest {

    @Autowired
    AgentTaskRepository repo;

    @Autowired
    EntityManager em;

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

    @Test
    void nameAndPayload_areStoredEncryptedAndDecryptTransparently() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(UUID.randomUUID());
        task.setName("Utrecht 3-bed watch");
        task.setPayload("{\"city\":\"Utrecht\"}");
        task.setNextRunAt(Instant.now());
        AgentTaskEntity saved = repo.saveAndFlush(task);
        em.clear();

        Object rawName = em.createNativeQuery("SELECT name FROM agent_tasks WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        assertThat(rawName).isNotEqualTo("Utrecht 3-bed watch");

        AgentTaskEntity reloaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Utrecht 3-bed watch");
        assertThat(reloaded.getPayload()).isEqualTo("{\"city\":\"Utrecht\"}");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(1);
    }
}

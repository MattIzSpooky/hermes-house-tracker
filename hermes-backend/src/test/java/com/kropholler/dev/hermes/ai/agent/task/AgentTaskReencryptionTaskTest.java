package com.kropholler.dev.hermes.ai.agent.task;

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
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class, AgentTaskReencryptionTask.class})
@TestPropertySource(properties = "hermes.encryption.current-version=2")
class AgentTaskReencryptionTaskTest {

    @Autowired AgentTaskRepository agentTaskRepository;
    @Autowired AgentTaskReencryptionTask task;
    @Autowired EntityManager em;

    @Test
    void reencryptBatch_migratesStaleRowsToCurrentVersionAndReturnsCountProcessed() {
        UUID id = UUID.randomUUID();
        String legacyName = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("Utrecht 3-bed watch");
        String legacyPayload = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("{\"city\":\"Utrecht\"}");
        em.createNativeQuery("""
                INSERT INTO agent_tasks (id, type, status, user_id, name, payload, encryption_key_version, next_run_at, created_at)
                VALUES (:id, 'WATCH', 'ACTIVE', :userId, :name, :payload, 1, :nextRunAt, now())
                """)
            .setParameter("id", id)
            .setParameter("userId", UUID.randomUUID())
            .setParameter("name", legacyName)
            .setParameter("payload", legacyPayload)
            .setParameter("nextRunAt", Instant.now())
            .executeUpdate();
        em.clear();

        int processed = task.reencryptBatch();
        em.clear();

        assertThat(processed).isEqualTo(1);
        AgentTaskEntity reloaded = agentTaskRepository.findById(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Utrecht 3-bed watch");
        assertThat(reloaded.getPayload()).isEqualTo("{\"city\":\"Utrecht\"}");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(2);

        Object rawName = em.createNativeQuery("SELECT name FROM agent_tasks WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawName.toString()).startsWith("2:");

        Object rawPayload = em.createNativeQuery("SELECT payload FROM agent_tasks WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawPayload.toString()).startsWith("2:");

        assertThat(task.reencryptBatch()).isEqualTo(0);
    }
}

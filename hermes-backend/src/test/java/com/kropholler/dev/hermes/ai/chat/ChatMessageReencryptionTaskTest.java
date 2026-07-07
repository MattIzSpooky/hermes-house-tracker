package com.kropholler.dev.hermes.ai.chat;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class, ChatMessageReencryptionTask.class})
@TestPropertySource(properties = "hermes.encryption.current-version=2")
class ChatMessageReencryptionTaskTest {

    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired ChatMessageReencryptionTask task;
    @Autowired EntityManager em;

    @Test
    void reencryptBatch_migratesStaleRowsToCurrentVersionAndReturnsCountProcessed() {
        UUID id = UUID.randomUUID();
        String legacyCiphertext = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("a secret message");
        em.createNativeQuery("""
                INSERT INTO chat_messages (id, session_id, user_id, role, content, encryption_key_version, created_at)
                VALUES (:id, :sessionId, :userId, 'USER', :content, 1, now())
                """)
            .setParameter("id", id)
            .setParameter("sessionId", UUID.randomUUID())
            .setParameter("userId", UUID.randomUUID())
            .setParameter("content", legacyCiphertext)
            .executeUpdate();
        em.clear();

        int processed = task.reencryptBatch();
        em.clear();

        assertThat(processed).isEqualTo(1);
        ChatMessageEntity reloaded = chatMessageRepository.findById(id).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("a secret message");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(2);

        Object rawContent = em.createNativeQuery("SELECT content FROM chat_messages WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawContent.toString()).startsWith("2:");

        assertThat(task.reencryptBatch()).isEqualTo(0);
    }
}

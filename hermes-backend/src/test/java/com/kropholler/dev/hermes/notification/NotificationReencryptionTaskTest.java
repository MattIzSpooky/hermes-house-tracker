package com.kropholler.dev.hermes.notification;

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
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class, NotificationReencryptionTask.class})
@TestPropertySource(properties = "hermes.encryption.current-version=2")
class NotificationReencryptionTaskTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationReencryptionTask task;
    @Autowired EntityManager em;

    @Test
    void reencryptBatch_migratesStaleRowsToCurrentVersionAndReturnsCountProcessed() {
        UUID id = UUID.randomUUID();
        String legacyTitle = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("Price alert");
        String legacyBody = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("Your favorite listing dropped 10%");
        em.createNativeQuery("""
                INSERT INTO notifications (id, user_id, title, body, encryption_key_version, read, created_at)
                VALUES (:id, :userId, :title, :body, 1, false, now())
                """)
            .setParameter("id", id)
            .setParameter("userId", UUID.randomUUID())
            .setParameter("title", legacyTitle)
            .setParameter("body", legacyBody)
            .executeUpdate();
        em.clear();

        int processed = task.reencryptBatch();
        em.clear();

        assertThat(processed).isEqualTo(1);
        NotificationEntity reloaded = notificationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Price alert");
        assertThat(reloaded.getBody()).isEqualTo("Your favorite listing dropped 10%");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(2);

        Object rawTitle = em.createNativeQuery("SELECT title FROM notifications WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawTitle.toString()).startsWith("2:");

        Object rawBody = em.createNativeQuery("SELECT body FROM notifications WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawBody.toString()).startsWith("2:");

        assertThat(task.reencryptBatch()).isEqualTo(0);
    }
}

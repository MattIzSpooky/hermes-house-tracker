package com.kropholler.dev.hermes.profile;

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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class})
class UserProfileRepositoryUpdateEmailTest {

    @Autowired UserProfileRepository repository;
    @Autowired EntityManager em;

    @Test
    void updateEmail_existingRow_updatesEmailOnlyAndLeavesUpdatedAtUntouched() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Dorpstraat");
        existing.setEmail("old@hermes.local");
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        existing.setUpdatedAt(originalUpdatedAt);
        repository.saveAndFlush(existing);
        em.clear();

        int updated = repository.updateEmail(userId, "new@hermes.local");
        em.clear();

        assertThat(updated).isEqualTo(1);
        UserProfileEntity reloaded = repository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@hermes.local");
        assertThat(reloaded.getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateEmail_noExistingRow_returnsZeroAndCreatesNoRow() {
        UUID userId = UUID.randomUUID();

        int updated = repository.updateEmail(userId, "user@hermes.local");

        assertThat(updated).isEqualTo(0);
        assertThat(repository.findById(userId)).isEmpty();
    }

    @Test
    void email_isStoredEncryptedAtRest() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setEmail("user@hermes.local");
        entity.setUpdatedAt(Instant.now());
        UserProfileEntity saved = repository.saveAndFlush(entity);
        em.clear();

        Object rawEmail = em.createNativeQuery("SELECT email FROM user_profiles WHERE user_id = :id")
            .setParameter("id", saved.getUserId())
            .getSingleResult();

        assertThat(rawEmail).isNotEqualTo("user@hermes.local");
        assertThat(repository.findById(userId).orElseThrow().getEmail()).isEqualTo("user@hermes.local");
        assertThat(saved.getEncryptionKeyVersion()).isEqualTo(1);
    }
}

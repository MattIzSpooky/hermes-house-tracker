package com.kropholler.dev.hermes.notification;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptionKeyVersionListener.class})
class NotificationRepositoryTest {

    @Autowired NotificationRepository repository;
    @Autowired EntityManager em;

    @Test
    void titleAndBody_areStoredEncryptedAndDecryptTransparently() {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(UUID.randomUUID());
        entity.setTitle("Price alert");
        entity.setBody("Your favorite listing dropped 10%");

        NotificationEntity saved = repository.saveAndFlush(entity);
        em.clear();

        Object rawTitle = em.createNativeQuery("SELECT title FROM notifications WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        assertThat(rawTitle).isNotEqualTo("Price alert");

        NotificationEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Price alert");
        assertThat(reloaded.getBody()).isEqualTo("Your favorite listing dropped 10%");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(1);
    }
}

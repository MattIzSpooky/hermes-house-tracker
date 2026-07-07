package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class})
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ScrapingSessionRepositoryTest {

    @Autowired
    private ScrapingSessionRepository repository;

    @Test
    void findFirstPendingWithLock_returnsPendingSession() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("Amsterdam");
        session.setPageLimit(3);
        session.setFundaUrl("https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D");
        repository.save(session);

        Optional<ScrapingSessionEntity> result = repository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ScrapingSessionStatus.PENDING);
    }
}

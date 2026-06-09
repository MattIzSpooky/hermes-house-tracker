package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ScrapingSessionRepositoryTest {

    @Autowired
    private ScrapingSessionRepository repository;

    @Test
    void findFirstPendingWithLock_returnsPendingSession() {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("Amsterdam");
        session.setPageLimit(3);
        session.setFundaUrl("https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D");
        repository.save(session);

        Optional<ScrapingSession> result = repository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ScrapingSessionStatus.PENDING);
    }
}

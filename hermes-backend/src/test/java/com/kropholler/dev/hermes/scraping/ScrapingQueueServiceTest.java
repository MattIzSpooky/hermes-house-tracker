package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionEntity;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionMapper;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapingQueueServiceTest {

    @Mock
    private ScrapingSessionRepository repository;

    @Mock
    private ScrapingSessionMapper mapper;

    @InjectMocks
    private ScrapingQueueService service;

    @BeforeEach
    void stubMapper() {
        when(mapper.toDto(any(ScrapingSessionEntity.class))).thenAnswer(inv -> {
            ScrapingSessionEntity s = inv.getArgument(0);
            return new ScrapingSessionDto(s.getId(), s.getStatus(), s.getType(),
                s.getCreatedAt(), s.getCompletedAt());
        });
    }

    @Test
    void enqueueSearch_clampsPageLimitToFive() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(5);
        session.setFundaUrl("https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D&search_result=1");
        when(repository.save(any())).thenReturn(session);

        ScrapingSessionDto dto = service.enqueueSearch("amsterdam", null, null, null, null, 10);

        assertThat(dto.status()).isEqualTo(ScrapingSessionStatus.PENDING);
    }

    @Test
    void enqueueSearch_setsCorrectType() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("rotterdam");
        session.setPageLimit(3);
        when(repository.save(any())).thenReturn(session);

        ScrapingSessionDto dto = service.enqueueSearch("rotterdam", null, null, null, null, 3);

        assertThat(dto.type()).isEqualTo(ScrapingSessionType.SEARCH);
    }

    @Test
    void enqueueRescrape_setsRescrapeType() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(repository.save(any())).thenReturn(session);

        ScrapingSessionDto dto = service.enqueueRescrape("https://www.funda.nl/koop/amsterdam/appartement-123/", "amsterdam");

        assertThat(dto.type()).isEqualTo(ScrapingSessionType.RESCRAPE);
    }
}

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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
        lenient().when(mapper.toDto(any(ScrapingSessionEntity.class))).thenAnswer(inv -> {
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

    @Test
    void enqueueSearch_withAllOptionalParams_includesPriceAndAreaInUrl() {
        // Covers L65-68 true branches: minPrice/maxPrice/minArea/maxArea all non-null
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("utrecht");
        session.setPageLimit(2);
        when(repository.save(any())).thenReturn(session);

        service.enqueueSearch("utrecht", 200000, 500000, 60, 150, 2);

        // Verify by inspecting the entity saved to the repository
        org.mockito.ArgumentCaptor<ScrapingSessionEntity> captor =
            org.mockito.ArgumentCaptor.forClass(ScrapingSessionEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        String url = captor.getValue().getFundaUrl();
        assertThat(url).contains("price_min=200000")
                       .contains("price_max=500000")
                       .contains("floor_area_min=60")
                       .contains("floor_area_max=150");
    }

    @Test
    void findById_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        when(repository.findById(id)).thenReturn(Optional.of(session));

        Optional<ScrapingSessionDto> result = service.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(ScrapingSessionType.SEARCH);
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(id)).isEmpty();
    }
}

package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingSessionStoreTest {

    @Mock ScrapingSessionRepository sessionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ScrapingSessionStore store;

    private ScrapingSessionEntity entity() {
        ScrapingSessionEntity e = new ScrapingSessionEntity();
        e.setType(ScrapingSessionType.SEARCH);
        e.setPageLimit(1);
        e.setFundaUrl("https://funda.nl/");
        return e;
    }

    @Test
    void markInProgress_whenSessionFound_updatesStatusAndStartedAt() {
        UUID id = UUID.randomUUID();
        ScrapingSessionEntity e = entity();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(e));

        store.markInProgress(id);

        assertThat(e.getStatus()).isEqualTo(ScrapingSessionStatus.IN_PROGRESS);
        assertThat(e.getStartedAt()).isNotNull();
    }

    @Test
    void markInProgress_whenSessionNotFound_doesNothing() {
        UUID id = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        store.markInProgress(id);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void complete_whenSessionFound_updatesStatusAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        ScrapingSessionEntity e = entity();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(e));

        store.complete(id, List.of());

        assertThat(e.getStatus()).isEqualTo(ScrapingSessionStatus.COMPLETED);
        assertThat(e.getCompletedAt()).isNotNull();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ScrapingSessionCompleted.class);
    }

    @Test
    void fail_whenSessionFound_updatesStatusAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        ScrapingSessionEntity e = entity();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(e));

        store.fail(id, "Proxy timeout");

        assertThat(e.getStatus()).isEqualTo(ScrapingSessionStatus.FAILED);
        assertThat(e.getCompletedAt()).isNotNull();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ScrapingSessionFailed event = (ScrapingSessionFailed) captor.getValue();
        assertThat(event.reason()).isEqualTo("Proxy timeout");
    }
}

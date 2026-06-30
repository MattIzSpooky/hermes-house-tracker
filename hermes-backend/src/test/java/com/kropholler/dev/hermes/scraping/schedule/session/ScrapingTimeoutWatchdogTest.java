package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingTimeoutWatchdogTest {

    @Mock ScrapingSessionRepository sessionRepository;
    @InjectMocks ScrapingTimeoutWatchdog watchdog;

    @Test
    void markTimedOutSessions_setsStatusAndCompletedAtOnStaleSessions() {
        ScrapingSession stale = new ScrapingSession();
        stale.setId(UUID.randomUUID());
        stale.setStatus(ScrapingSessionStatus.IN_PROGRESS);
        stale.setType(ScrapingSessionType.SEARCH);
        stale.setFundaUrl("https://funda.nl");
        stale.setPageLimit(1);
        stale.setStartedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(sessionRepository.findByStatusAndStartedAtBefore(eq(ScrapingSessionStatus.IN_PROGRESS), any()))
            .thenReturn(List.of(stale));

        watchdog.markTimedOutSessions();

        assertThat(stale.getStatus()).isEqualTo(ScrapingSessionStatus.TIMED_OUT);
        assertThat(stale.getCompletedAt()).isNotNull();
        verify(sessionRepository).saveAll(List.of(stale));
    }

    @Test
    void markTimedOutSessions_doesNothingWhenNoStaleSessions() {
        when(sessionRepository.findByStatusAndStartedAtBefore(any(), any())).thenReturn(List.of());

        watchdog.markTimedOutSessions();

        verify(sessionRepository).saveAll(List.of());
    }
}

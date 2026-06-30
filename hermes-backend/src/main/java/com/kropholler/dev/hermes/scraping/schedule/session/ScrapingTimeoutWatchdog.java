package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class ScrapingTimeoutWatchdog {

    private static final int TIMEOUT_MINUTES = 3;

    private final ScrapingSessionRepository sessionRepository;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markTimedOutSessions() {
        Instant cutoff = Instant.now().minus(TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        List<ScrapingSessionEntity> stale = sessionRepository
            .findByStatusAndStartedAtBefore(ScrapingSessionStatus.IN_PROGRESS, cutoff);

        for (ScrapingSessionEntity session : stale) {
            log.warn("Session {} timed out after {} minutes", session.getId(), TIMEOUT_MINUTES);
            session.setStatus(ScrapingSessionStatus.TIMED_OUT);
            session.setCompletedAt(Instant.now());
        }
        sessionRepository.saveAll(stale);
    }
}

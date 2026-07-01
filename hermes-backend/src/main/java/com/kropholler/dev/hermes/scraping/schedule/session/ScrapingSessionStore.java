package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ScrapingSessionStore {

    private final ScrapingSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    void markInProgress(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(ScrapingSessionStatus.IN_PROGRESS);
            session.setStartedAt(Instant.now());
        });
    }

    @Transactional
    void complete(UUID sessionId, List<RawListing> listings) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(ScrapingSessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            eventPublisher.publishEvent(new ScrapingSessionCompleted(sessionId, session.getType(), listings));
        });
    }

    @Transactional
    void fail(UUID sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(ScrapingSessionStatus.FAILED);
            session.setCompletedAt(Instant.now());
            eventPublisher.publishEvent(new ScrapingSessionFailed(sessionId, reason));
        });
    }
}

package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ListingNotFound;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapingWorker {

    private final ScrapingSessionRepository sessionRepository;
    private final FundaProxyClient proxyClient;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @Transactional
    public void process(ScrapingSession session) {
        session.setStatus(ScrapingSessionStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        try {
            List<RawListing> listings = scrapeAllPages(session);
            session.setStatus(ScrapingSessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            eventPublisher.publishEvent(new ScrapingSessionCompleted(session.getId(), listings));
        } catch (Exception e) {
            log.error("Scraping session {} failed", session.getId(), e);
            session.setStatus(ScrapingSessionStatus.FAILED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            eventPublisher.publishEvent(new ScrapingSessionFailed(session.getId(), e.getMessage()));
        }
    }

    private List<RawListing> scrapeAllPages(ScrapingSession session) {
        if (session.getType() == ScrapingSessionType.RESCRAPE) {
            String fundaId = proxyClient.extractFundaId(session.getTargetListingUrl());
            Optional<RawListing> listing = proxyClient.getListing(fundaId);
            if (listing.isEmpty()) {
                eventPublisher.publishEvent(new ListingNotFound(fundaId));
            }
            return listing.map(List::of).orElse(List.of());
        }

        List<RawListing> all = new ArrayList<>();
        int limit = Math.min(session.getPageLimit(), 5);
        for (int page = 1; page <= limit; page++) {
            List<RawListing> pageResults = proxyClient.search(
                session.getCity(), session.getMinPrice(), session.getMaxPrice(),
                session.getMinArea(), session.getMaxArea(), page);
            all.addAll(pageResults);
            if (pageResults.isEmpty()) break;
        }
        return all;
    }
}

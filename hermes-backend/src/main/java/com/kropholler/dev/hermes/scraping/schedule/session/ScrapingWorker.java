package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.funda.ListingNotFound;
import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import com.kropholler.dev.hermes.funda.FundaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class ScrapingWorker {

    private final ScrapingSessionStore sessionStore;
    private final FundaClient proxyClient;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    public void process(ScrapingSessionEntity session) {
        sessionStore.markInProgress(session.getId());

        try {
            List<RawListing> listings = scrapeAllPages(session);
            sessionStore.complete(session.getId(), listings);
        } catch (Exception e) {
            log.error("Scraping session {} failed", session.getId(), e);
            sessionStore.fail(session.getId(), e.getMessage());
        }
    }

    private List<RawListing> scrapeAllPages(ScrapingSessionEntity session) {
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

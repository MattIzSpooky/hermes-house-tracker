package com.kropholler.dev.hermes.scraping.internal;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapingWorker {

    private final ScrapingSessionRepository sessionRepository;
    private final FundaScraperService scraperService;
    private final FundaUrlBuilder urlBuilder;
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
            return scraperService.scrapeSearchPage(session.getTargetListingUrl(), session.getCity());
        }

        List<RawListing> all = new ArrayList<>();
        int limit = Math.min(session.getPageLimit(), 5);
        for (int page = 1; page <= limit; page++) {
            String pageUrl = urlBuilder.build(
                session.getCity(), session.getMinPrice(), session.getMaxPrice(),
                session.getMinArea(), session.getMaxArea(), page
            );
            List<RawListing> pageResults = scraperService.scrapeSearchPage(pageUrl, session.getCity());
            all.addAll(pageResults);
            if (pageResults.isEmpty()) break;
        }
        return all;
    }
}

package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceHistoryService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NightlyRescrapeScheduler {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;
    private final PriceHistoryService priceHistoryService;
    private final ObservationRegistry observationRegistry;

    @Scheduled(cron = "0 0 2 * * *")
    public void enqueueNightlyRescrapes() {
        Observation.createNotStarted("scheduler.nightly-rescrape", observationRegistry)
            .observe(this::doEnqueue);
    }

    private void doEnqueue() {
        log.info("Starting nightly rescrape job");
        int count = 0;
        int page = 0;
        Page<ListingDto> batch;

        do {
            batch = listingService.findAllActive(PageRequest.of(page, 100));
            for (ListingDto listing : batch.getContent()) {
                queueService.enqueueRescrape(listing.url(), listing.city());
                count++;
            }
            page++;
        } while (batch.hasNext());

        log.info("Nightly rescrape enqueued {} sessions, starting price history refresh", count);
        priceHistoryService.refreshAll();
        log.info("Nightly rescrape job complete");
    }
}

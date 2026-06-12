package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class DeletedListingCleanupScheduler {

    private final ListingService listingService;

    @Scheduled(cron = "0 0 3 1,15 * *")
    public void cleanupDeletedListings() {
        log.info("Starting biweekly deleted listing cleanup");
        listingService.deleteAllDeleted();
        log.info("Deleted listing cleanup complete");
    }
}

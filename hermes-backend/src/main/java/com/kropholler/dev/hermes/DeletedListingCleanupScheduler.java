package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class DeletedListingCleanupScheduler {

    private final ListingRepository listingRepository;

    @Scheduled(cron = "0 0 3 1,15 * *")
    @Transactional
    public void cleanupDeletedListings() {
        log.info("Starting biweekly deleted listing cleanup");
        listingRepository.deleteAllByDeletedAtIsNotNull();
        log.info("Deleted listing cleanup complete");
    }
}

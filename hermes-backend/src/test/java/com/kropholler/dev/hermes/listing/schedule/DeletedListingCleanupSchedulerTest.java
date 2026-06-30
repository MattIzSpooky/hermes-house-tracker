package com.kropholler.dev.hermes.listing.schedule;

import com.kropholler.dev.hermes.listing.ListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeletedListingCleanupSchedulerTest {

    @Mock ListingService listingService;
    @InjectMocks DeletedListingCleanupScheduler scheduler;

    @Test
    void cleanupDeletedListings_delegatesToService() {
        scheduler.cleanupDeletedListings();

        verify(listingService).deleteAllDeleted();
    }
}

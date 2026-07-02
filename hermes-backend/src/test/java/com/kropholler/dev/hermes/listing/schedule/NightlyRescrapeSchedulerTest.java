package com.kropholler.dev.hermes.listing.schedule;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightlyRescrapeSchedulerTest {

    @Mock ListingService listingService;
    @Mock ScrapingQueueService queueService;
    NightlyRescrapeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NightlyRescrapeScheduler(listingService, queueService, ObservationRegistry.NOOP);
    }

    private ListingDto dto(String url, String city) {
        return new ListingDto(UUID.randomUUID(), "funda-1", url,
            "Straat", "1", null, "1000AA", city, "Noord-Holland",
            Instant.now(), Instant.now(), 250000, ListingStatus.FOR_SALE,
            null, 70, 3, 2, "B", null, null);
    }

    @Test
    void enqueueNightlyRescrapes_enqueuesEachActiveListing() {
        ListingDto a = dto("https://funda.nl/a", "Amsterdam");
        ListingDto b = dto("https://funda.nl/b", "Utrecht");

        when(listingService.findAllActive(PageRequest.of(0, 100)))
            .thenReturn(new PageImpl<>(List.of(a, b)));

        scheduler.enqueueNightlyRescrapes();

        verify(queueService).enqueueRescrape("https://funda.nl/a", "Amsterdam");
        verify(queueService).enqueueRescrape("https://funda.nl/b", "Utrecht");
        verify(listingService).refreshAllPriceHistory();
    }
}

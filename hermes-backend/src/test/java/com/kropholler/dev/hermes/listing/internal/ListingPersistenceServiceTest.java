package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingSnapshotsCreated;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingPersistenceServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingSnapshotRepository snapshotRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ListingPersistenceService service;

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void onScrapingCompleted_createsNewListingAndSnapshot() {
        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, 75, 3, "A", null, "FOR_SALE"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw), null);

        Listing listing = new Listing();
        listing.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.save(any())).thenReturn(listing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onScrapingSessionCompleted(event);

        verify(listingRepository).save(any());
        verify(snapshotRepository).save(any());
        verify(eventPublisher).publishEvent(any(ListingSnapshotsCreated.class));
    }

    @Test
    void onScrapingCompleted_updatesLastSeenAtForExistingListing() {
        Listing existing = new Listing();
        existing.setFundaId("12345678");
        var originalLastSeen = existing.getLastSeenAt();

        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            460000, 75, 3, "A", null, "FOR_SALE"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw), null);

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getLastSeenAt()).isAfterOrEqualTo(originalLastSeen);
        verify(snapshotRepository).save(any());
    }

    @Test
    void onScrapingCompleted_restoresMdcCorrelationIdDuringExecution() {
        AtomicReference<String> capturedMdc = new AtomicReference<>();
        Listing existingListing = new Listing();
        existingListing.setFundaId("12345678");

        when(listingRepository.findByFundaId("12345678")).thenAnswer(inv -> {
            capturedMdc.set(MDC.get("correlationId"));
            return Optional.of(existingListing);
        });
        when(listingRepository.save(any())).thenReturn(existingListing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RawListing raw = new RawListing(
            "12345678", "https://f.nl/1", "Street", "1", null,
            "1234AB", "Amsterdam", "Noord-Holland", null, null, null, null, null, "FOR_SALE");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(
            UUID.randomUUID(), List.of(raw), "chain-corr");

        service.onScrapingSessionCompleted(event);

        assertThat(capturedMdc.get()).isEqualTo("chain-corr");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void onScrapingCompleted_publishesListingSnapshotsCreatedWithCorrelationId() {
        Listing existingListing = new Listing();
        existingListing.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existingListing));
        when(listingRepository.save(any())).thenReturn(existingListing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RawListing raw = new RawListing(
            "12345678", "https://f.nl/1", "Street", "1", null,
            "1234AB", "Amsterdam", "Noord-Holland", null, null, null, null, null, "FOR_SALE");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(
            UUID.randomUUID(), List.of(raw), "chain-corr");

        service.onScrapingSessionCompleted(event);

        ArgumentCaptor<ListingSnapshotsCreated> captor = ArgumentCaptor.forClass(ListingSnapshotsCreated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("chain-corr");
    }
}

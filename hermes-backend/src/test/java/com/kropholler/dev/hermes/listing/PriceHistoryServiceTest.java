package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.scraping.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.RawPriceChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private FundaProxyFacade proxyFacade;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PriceHistoryService service;

    @Test
    void fetchAndStore_savesNewEntry() {
        UUID listingId = UUID.randomUUID();
        Instant ts = Instant.parse("2024-05-15T00:00:00Z");
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), ts);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));
        when(priceHistoryRepository.existsByListingIdAndTimestamp(listingId, ts)).thenReturn(false);

        service.fetchAndStore(listingId, "12345678");

        ArgumentCaptor<PriceHistoryEntry> captor = ArgumentCaptor.forClass(PriceHistoryEntry.class);
        verify(priceHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualTo(350000);
        assertThat(captor.getValue().getStatus()).isEqualTo("asking_price");
        assertThat(captor.getValue().getTimestamp()).isEqualTo(ts);
    }

    @Test
    void fetchAndStore_skipsDuplicateEntry() {
        UUID listingId = UUID.randomUUID();
        Instant ts = Instant.parse("2024-05-15T00:00:00Z");
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), ts);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));
        when(priceHistoryRepository.existsByListingIdAndTimestamp(listingId, ts)).thenReturn(true);

        service.fetchAndStore(listingId, "12345678");

        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    void fetchAndStore_skipsEntryWithNullTimestamp() {
        UUID listingId = UUID.randomUUID();
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), null);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));

        service.fetchAndStore(listingId, "12345678");

        verify(priceHistoryRepository, never()).save(any());
        verify(priceHistoryRepository, never()).existsByListingIdAndTimestamp(any(), any());
    }

    @Test
    void refreshAll_publishesPriceHistoryUpdated() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");
        UUID listingId = UUID.randomUUID();
        listing.setId(listingId);

        when(listingRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(listing)));
        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of());

        service.refreshAll();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PriceHistoryUpdated.class);
    }

    @Test
    void onListingCreated_callsFetchAndStoreAndPublishesEvent() {
        UUID listingId = UUID.randomUUID();
        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of());

        service.onListingCreated(new ListingCreated(listingId, "12345678"));

        verify(proxyFacade).getPriceHistory("12345678");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PriceHistoryUpdated.class);
        assertThat(((PriceHistoryUpdated) captor.getValue()).listingIds()).contains(listingId);
    }
}

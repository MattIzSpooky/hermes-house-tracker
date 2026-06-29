package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.scraping.funda.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.funda.RawPriceChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jms.core.JmsTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private FundaProxyFacade proxyFacade;
    @Mock private JmsTemplate jmsTemplate;

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
    void refreshAll_sendsJmsMessagePerListing() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");
        UUID listingId = UUID.randomUUID();
        listing.setId(listingId);

        when(listingRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(listing)));

        service.refreshAll();

        ArgumentCaptor<Object> cmdCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), cmdCaptor.capture());
        assertThat(cmdCaptor.getValue()).isInstanceOf(FetchPriceHistoryCommand.class);
        FetchPriceHistoryCommand cmd = (FetchPriceHistoryCommand) cmdCaptor.getValue();
        assertThat(cmd.listingId()).isEqualTo(listingId);
        assertThat(cmd.fundaId()).isEqualTo("12345678");
    }
}

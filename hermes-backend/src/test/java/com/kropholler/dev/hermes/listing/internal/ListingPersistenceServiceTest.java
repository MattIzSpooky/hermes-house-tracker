package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.async.command.FetchListingDetailsCommand;
import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingPersistenceService;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.funda.ListingNotFound;
import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingPersistenceServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private JmsTemplate jmsTemplate;

    @InjectMocks
    private ListingPersistenceService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void fireAfterCommit() {
        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
            .forEach(TransactionSynchronization::afterCommit);
    }

    private static RawListing rawListing(String fundaId, String status) {
        return new RawListing(
            fundaId, "https://www.funda.nl/koop/amsterdam/huis-" + fundaId + "/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, status,
            null, null, null, null, null, null
        );
    }

    @Test
    void newListing_setsStatusAndSendsBothFetchCommands() {
        RawListing raw = rawListing("12345678", "FOR_SALE");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), ScrapingSessionType.SEARCH, List.of(raw));

        Listing saved = new Listing();
        saved.setId(UUID.randomUUID());
        saved.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.saveAndFlush(any())).thenReturn(saved);

        service.onScrapingSessionCompleted(event);
        fireAfterCommit();

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).saveAndFlush(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getStatus()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(listingCaptor.getValue().getLastUpdatedAt()).isNotNull();

        // Details command sent for all listings
        ArgumentCaptor<FetchListingDetailsCommand> detailsCaptor =
            ArgumentCaptor.forClass(FetchListingDetailsCommand.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.LISTING_DETAILS_FETCH), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue().fundaId()).isEqualTo("12345678");
        assertThat(detailsCaptor.getValue().listingId()).isEqualTo(saved.getId());

        // Price history command sent only for new listings
        ArgumentCaptor<FetchPriceHistoryCommand> priceCaptor =
            ArgumentCaptor.forClass(FetchPriceHistoryCommand.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), priceCaptor.capture());
        assertThat(priceCaptor.getValue().fundaId()).isEqualTo("12345678");
    }

    @Test
    void existingListing_onRescrape_sendsBothFetchCommands() {
        Listing existing = new Listing();
        existing.setId(UUID.randomUUID());
        existing.setFundaId("12345678");

        RawListing raw = rawListing("12345678", "UNDER_OFFER");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), ScrapingSessionType.RESCRAPE, List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.saveAndFlush(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);
        fireAfterCommit();

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.UNDER_OFFER);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.LISTING_DETAILS_FETCH), any(FetchListingDetailsCommand.class));
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), any(FetchPriceHistoryCommand.class));
    }

    @Test
    void existingListing_onSearch_sendsDetailsCommandOnly() {
        Listing existing = new Listing();
        existing.setId(UUID.randomUUID());
        existing.setFundaId("12345678");

        RawListing raw = rawListing("12345678", "UNDER_OFFER");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), ScrapingSessionType.SEARCH, List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.saveAndFlush(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);
        fireAfterCommit();

        verify(jmsTemplate).convertAndSend(eq(JmsQueues.LISTING_DETAILS_FETCH), any(FetchListingDetailsCommand.class));
        verify(jmsTemplate, never()).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), any(Object.class));
    }

    @Test
    void onListingNotFound_setsStatusDeletedAndDeletedAt() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(listing));
        when(listingRepository.save(any())).thenReturn(listing);

        service.onListingNotFound(new ListingNotFound("12345678"));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
        assertThat(listing.getDeletedAt()).isNotNull();
        assertThat(listing.getLastUpdatedAt()).isNotNull();
    }
}

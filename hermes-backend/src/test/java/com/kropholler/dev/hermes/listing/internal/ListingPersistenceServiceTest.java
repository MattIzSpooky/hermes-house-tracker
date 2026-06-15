package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.ListingNotFound;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

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

    @Test
    void newListing_setsStatusAndSendsFetchCommand() {
        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, "FOR_SALE",
            null, null, null, null, null, null
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        Listing saved = new Listing();
        saved.setId(UUID.randomUUID());
        saved.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.save(any())).thenReturn(saved);

        service.onScrapingSessionCompleted(event);

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getStatus()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(listingCaptor.getValue().getLastUpdatedAt()).isNotNull();

        ArgumentCaptor<FetchPriceHistoryCommand> cmdCaptor = ArgumentCaptor.forClass(FetchPriceHistoryCommand.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().fundaId()).isEqualTo("12345678");
        assertThat(cmdCaptor.getValue().listingId()).isEqualTo(saved.getId());
    }

    @Test
    void existingListing_updatesStatusAndDoesNotSendFetchCommand() {
        Listing existing = new Listing();
        existing.setFundaId("12345678");

        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            460000, "UNDER_OFFER",
            null, null, null, null, null, null
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.UNDER_OFFER);
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(Object.class));
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

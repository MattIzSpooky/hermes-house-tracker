package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.scraping.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.RawListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingDetailsConsumerTest {

    @Mock private ListingRepository listingRepository;
    @Mock private FundaProxyFacade proxyFacade;

    @InjectMocks
    private ListingDetailsConsumer consumer;

    private static RawListing richListing(String fundaId) {
        return new RawListing(
            fundaId, "https://www.funda.nl/koop/amsterdam/huis-" + fundaId + "/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, "FOR_SALE",
            "Mooie woning", 95, 4, 3, "A", 120
        );
    }

    @Test
    void onMessage_updatesAllSixFields_whenProxyReturnData() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "12345678");

        RawListing raw = richListing("12345678");
        when(proxyFacade.getListing("12345678")).thenReturn(Optional.of(raw));

        Listing listing = new Listing();
        listing.setId(listingId);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any())).thenReturn(listing);

        consumer.onMessage(command);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Mooie woning");
        assertThat(saved.getLivingAreaM2()).isEqualTo(95);
        assertThat(saved.getRooms()).isEqualTo(4);
        assertThat(saved.getBedrooms()).isEqualTo(3);
        assertThat(saved.getEnergyLabel()).isEqualTo("A");
        assertThat(saved.getPlotAreaM2()).isEqualTo(120);
    }

    @Test
    void onMessage_doesNothing_whenProxyReturnsEmpty() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "99999999");

        when(proxyFacade.getListing("99999999")).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_doesNothing_whenListingNotFoundInDb() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "12345678");

        RawListing raw = richListing("12345678");
        when(proxyFacade.getListing("12345678")).thenReturn(Optional.of(raw));
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }
}

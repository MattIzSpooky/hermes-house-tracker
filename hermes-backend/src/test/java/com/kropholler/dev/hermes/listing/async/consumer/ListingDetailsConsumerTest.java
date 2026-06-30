package com.kropholler.dev.hermes.listing.async.consumer;

import com.kropholler.dev.hermes.funda.FundaClient;
import com.kropholler.dev.hermes.listing.async.command.FetchListingDetailsCommand;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.funda.RawListing;
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
    @Mock private FundaClient fundaClient;

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
        when(fundaClient.getListing("12345678")).thenReturn(Optional.of(raw));

        ListingEntity listing = new ListingEntity();
        listing.setId(listingId);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any())).thenReturn(listing);

        consumer.onMessage(command);

        ArgumentCaptor<ListingEntity> captor = ArgumentCaptor.forClass(ListingEntity.class);
        verify(listingRepository).save(captor.capture());
        ListingEntity saved = captor.getValue();
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

        when(fundaClient.getListing("99999999")).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_doesNothing_whenListingNotFoundInDb() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "12345678");

        RawListing raw = richListing("12345678");
        when(fundaClient.getListing("12345678")).thenReturn(Optional.of(raw));
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }
}

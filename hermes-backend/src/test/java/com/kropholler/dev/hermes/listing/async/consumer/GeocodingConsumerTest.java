package com.kropholler.dev.hermes.listing.async.consumer;

import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.NominatimClient;
import com.kropholler.dev.hermes.listing.geocoding.NominatimResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodingConsumerTest {

    @Mock private ListingRepository listingRepository;
    @Mock private NominatimClient nominatimClient;
    @InjectMocks private GeocodingConsumer consumer;

    @Test
    void onMessage_geocodesListingAndUpdatesGeometry() {
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setHouseNumber("9");
        listing.setStreet("Rentmeesterlaan");
        listing.setCity("Weert");

        NominatimResponse response = new NominatimResponse(
            "51.2574224", "5.6972390",
            List.of("51.2573724", "51.2574724", "5.6971890", "5.6972890"),
            30, "place", "9, Rentmeesterlaan, Weert"
        );

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(nominatimClient.geocodeAddress("9", "Rentmeesterlaan", "Weert"))
            .thenReturn(Optional.of(response));

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository).updateLocation(listingId, 5.6972390, 51.2574224);
        verify(listingRepository).updateBoundingBox(listingId, 5.6971890, 51.2573724, 5.6972890, 51.2574724);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_nominatimReturnsEmpty_doesNotUpdateGeometry() {
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setHouseNumber("1");
        listing.setStreet("Onbekend");
        listing.setCity("Nergens");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(nominatimClient.geocodeAddress(any(), any(), any())).thenReturn(Optional.empty());

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository, never()).updateLocation(any(), anyDouble(), anyDouble());
        verify(listingRepository, never()).save(any());
    }
}

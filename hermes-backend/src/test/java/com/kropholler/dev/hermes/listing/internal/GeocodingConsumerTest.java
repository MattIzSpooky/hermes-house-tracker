package com.kropholler.dev.hermes.listing.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodingConsumerTest {

    @Mock private ListingRepository listingRepository;
    @Mock private NominatimClient nominatimClient;
    @InjectMocks private GeocodingConsumer consumer;

    @Test
    void onMessage_geocodesListingAndSavesLocation() {
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

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        assertThat(captor.getValue().getLocation()).isInstanceOf(Point.class);
        assertThat(captor.getValue().getBoundingBox()).isNotNull();
    }

    @Test
    void onMessage_nominatimReturnsEmpty_doesNotSave() {
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setHouseNumber("1");
        listing.setStreet("Onbekend");
        listing.setCity("Nergens");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(nominatimClient.geocodeAddress(any(), any(), any())).thenReturn(Optional.empty());

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository, never()).save(any());
    }
}

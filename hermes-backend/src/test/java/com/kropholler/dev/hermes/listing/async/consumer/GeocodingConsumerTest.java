package com.kropholler.dev.hermes.listing.async.consumer;

import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
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
    @Mock private GeocodingService geocodingService;
    @InjectMocks private GeocodingConsumer consumer;

    @Test
    void onMessage_geocodesListingAndUpdatesGeometry() {
        UUID listingId = UUID.randomUUID();
        ListingEntity listing = new ListingEntity();
        listing.setHouseNumber("9");
        listing.setStreet("Rentmeesterlaan");
        listing.setCity("Weert");

        GeocodeResult response = new GeocodeResult(
            Double.parseDouble("5.6972390"), Double.parseDouble("51.2574224"),
            List.of("51.2573724", "51.2574724", "5.6971890", "5.6972890")
        );

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(geocodingService.geocodeAddress("9", "Rentmeesterlaan", "Weert"))
            .thenReturn(Optional.of(response));

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository).updateLocation(listingId, 5.6972390, 51.2574224);
        verify(listingRepository).updateBoundingBox(listingId, 5.6971890, 51.2573724, 5.6972890, 51.2574724);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_nominatimReturnsEmpty_doesNotUpdateGeometry() {
        UUID listingId = UUID.randomUUID();
        ListingEntity listing = new ListingEntity();
        listing.setHouseNumber("1");
        listing.setStreet("Onbekend");
        listing.setCity("Nergens");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(geocodingService.geocodeAddress(any(), any(), any())).thenReturn(Optional.empty());

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository, never()).updateLocation(any(), anyDouble(), anyDouble());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_listingNotFound_skipsGeocoding() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
    }

    @Test
    void onMessage_streetNull_skipsGeocoding() {
        UUID listingId = UUID.randomUUID();
        ListingEntity listing = new ListingEntity();
        listing.setStreet(null);
        listing.setCity("Amsterdam");
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
    }

    @Test
    void onMessage_cityNull_skipsGeocoding() {
        UUID listingId = UUID.randomUUID();
        ListingEntity listing = new ListingEntity();
        listing.setStreet("Dorpstraat");
        listing.setCity(null);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
    }
}

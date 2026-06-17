package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.City;
import com.kropholler.dev.hermes.listing.internal.CityRepository;
import com.kropholler.dev.hermes.listing.internal.NominatimClient;
import com.kropholler.dev.hermes.listing.internal.NominatimResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private CityRepository cityRepository;
    @Mock private NominatimClient nominatimClient;
    @InjectMocks private GeocodingService service;

    @Test
    void findOrFetchCity_cachedCity_returnsWithoutCallingNominatim() {
        City city = new City();
        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.of(city));

        Optional<City> result = service.findOrFetchCity("Weert");

        assertThat(result).isPresent();
        verifyNoInteractions(nominatimClient);
    }

    @Test
    void findOrFetchCity_notCached_fetchesFromNominatimAndSaves() {
        NominatimResponse response = new NominatimResponse(
            "51.2355829", "5.7050797",
            List.of("51.1804207", "51.2905755", "5.5660454", "5.7917701"),
            14, "municipality", "Weert, Limburg, Netherlands"
        );

        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Weert")).thenReturn(Optional.of(response));
        when(cityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<City> result = service.findOrFetchCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().getLocation()).isInstanceOf(Point.class);
        verify(cityRepository).save(any(City.class));
    }

    @Test
    void findOrFetchCity_nominatimReturnsEmpty_returnsEmpty() {
        when(cityRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Unknown")).thenReturn(Optional.empty());

        Optional<City> result = service.findOrFetchCity("Unknown");

        assertThat(result).isEmpty();
        verify(cityRepository, never()).save(any());
    }

    @Test
    void geocodeAddress_delegatesToNominatimClient() {
        NominatimResponse response = new NominatimResponse(
            "51.2574224", "5.6972390",
            List.of("51.2573724", "51.2574724", "5.6971890", "5.6972890"),
            30, "place", "9, Rentmeesterlaan, Weert"
        );
        when(nominatimClient.geocodeAddress("9", "Rentmeesterlaan", "Weert"))
            .thenReturn(Optional.of(response));

        Optional<double[]> result = service.geocodeAddress("9", "Rentmeesterlaan", "Weert");

        assertThat(result).isPresent();
        assertThat(result.get()[0]).isEqualTo(51.2574224, within(0.0001));
        assertThat(result.get()[1]).isEqualTo(5.6972390, within(0.0001));
    }
}

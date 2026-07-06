package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.city.CityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        CityEntity city = new CityEntity();
        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.of(city));

        Optional<CityEntity> result = service.findOrFetchCity("Weert");

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

        Optional<CityEntity> result = service.findOrFetchCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().getLatitude()).isEqualTo(51.2355829, within(0.0001));
        assertThat(result.get().getLongitude()).isEqualTo(5.7050797, within(0.0001));
        verify(cityRepository).save(any(CityEntity.class));
    }

    @Test
    void findOrFetchCity_nominatimReturnsEmpty_returnsEmpty() {
        when(cityRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Unknown")).thenReturn(Optional.empty());

        Optional<CityEntity> result = service.findOrFetchCity("Unknown");

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

        Optional<GeocodeResult> result = service.geocodeAddress("9", "Rentmeesterlaan", "Weert");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(51.2574224, within(0.0001));
        assertThat(result.get().lon()).isEqualTo(5.6972390, within(0.0001));
    }

    @Test
    void geocodeCity_cachedCity_returnsGeocodeResult() {
        CityEntity city = new CityEntity();
        city.setLongitude(5.7050797);
        city.setLatitude(51.2355829);
        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.of(city));

        Optional<GeocodeResult> result = service.geocodeCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(51.2355829, within(0.0001));
        assertThat(result.get().lon()).isEqualTo(5.7050797, within(0.0001));
        verifyNoInteractions(nominatimClient);
    }

    @Test
    void geocodeCity_notFound_returnsEmpty() {
        when(cityRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Unknown")).thenReturn(Optional.empty());

        Optional<GeocodeResult> result = service.geocodeCity("Unknown");

        assertThat(result).isEmpty();
    }
}

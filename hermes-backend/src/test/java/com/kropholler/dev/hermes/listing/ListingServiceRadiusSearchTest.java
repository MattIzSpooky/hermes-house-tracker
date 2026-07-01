package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceRadiusSearchTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private ListingMapper mapper;
    @Mock private GeocodingService geocodingService;

    @InjectMocks
    private ListingService service;

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);
    private static final GeocodeResult AMSTERDAM = new GeocodeResult(4.9041, 52.3676, null);

    private void stubEmptyPage() {
        when(listingRepository.findAll(any(Specification.class), eq(PAGEABLE)))
            .thenReturn(new PageImpl<>(List.of()));
    }

    // --- geocoding delegation ---

    @Test
    void findAll_radiusWithStreetAndCity_geocodesAddressWithCorrectArguments() {
        var params = new ListingSearchParams("Kerkstraat", "13", null, null, "Amsterdam", null, null, null, null, null, 5);
        when(geocodingService.geocodeAddress("13", "Kerkstraat", "Amsterdam")).thenReturn(Optional.of(AMSTERDAM));
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).geocodeAddress("13", "Kerkstraat", "Amsterdam");
        verifyNoMoreInteractions(geocodingService);
    }

    @Test
    void findAll_radiusWithStreetAndNoCity_geocodesWithEmptyStringForCity() {
        // When no city is given, an empty string is passed to Nominatim.
        // "Kerkstraat" without a city is highly ambiguous in the Netherlands.
        var params = new ListingSearchParams("Kerkstraat", "13", null, null, null, null, null, null, null, null, 5);
        when(geocodingService.geocodeAddress("13", "Kerkstraat", "")).thenReturn(Optional.of(AMSTERDAM));
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).geocodeAddress("13", "Kerkstraat", "");
    }

    @Test
    void findAll_radiusWithNullHouseNumber_passesEmptyStringForHouseNumber() {
        var params = new ListingSearchParams("Kerkstraat", null, null, null, "Amsterdam", null, null, null, null, null, 5);
        when(geocodingService.geocodeAddress("", "Kerkstraat", "Amsterdam")).thenReturn(Optional.of(AMSTERDAM));
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).geocodeAddress("", "Kerkstraat", "Amsterdam");
    }

    @Test
    void findAll_radiusWithCityOnly_delegatesToFindOrFetchCityNotGeocodeAddress() {
        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, null, null, 5);
        when(geocodingService.findOrFetchCity("Amsterdam")).thenReturn(Optional.empty());
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).findOrFetchCity("Amsterdam");
        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
    }

    @Test
    void findAll_radiusWithCityOnly_geocodingSucceeds_usesCityCoordinates() {
        CityEntity city = new CityEntity();
        city.setLatitude(52.3676);
        city.setLongitude(4.9041);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, null, null, 5);
        when(geocodingService.findOrFetchCity("Amsterdam")).thenReturn(Optional.of(city));
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).findOrFetchCity("Amsterdam");
        verify(listingRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // --- silent failure when geocoding returns empty ---

    /**
     * Documents a defect: when geocoding of the search center address fails,
     * the radius filter is silently omitted and the query runs without any radius
     * constraint. The user gets unexpected results (possibly all listings) rather
     * than an error or empty page.
     */
    @Test
    void findAll_radiusGeocodingFails_silentlySkipsRadiusAndStillCallsRepository() {
        var params = new ListingSearchParams("Kerkstraat", "13", null, null, null, null, null, null, null, null, 5);
        when(geocodingService.geocodeAddress("13", "Kerkstraat", "")).thenReturn(Optional.empty());
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        // Repository is still called — the search runs without a radius constraint
        verify(listingRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    @Test
    void findAll_radiusCityGeocodingFails_silentlySkipsRadiusAndStillCallsRepository() {
        var params = new ListingSearchParams(null, null, null, null, "UnknownCity", null, null, null, null, null, 5);
        when(geocodingService.findOrFetchCity("UnknownCity")).thenReturn(Optional.empty());
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(listingRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // --- hasRadiusSearch() conditions ---

    @Test
    void hasRadiusSearch_trueWhenStreetAndRadiusKmBothSet() {
        var params = new ListingSearchParams("Kerkstraat", "13", null, null, null, null, null, null, null, null, 5);
        assertThat(params.hasRadiusSearch()).isTrue();
    }

    @Test
    void hasRadiusSearch_trueWhenCityAndRadiusKmBothSet() {
        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, null, null, 5);
        assertThat(params.hasRadiusSearch()).isTrue();
    }

    @Test
    void hasRadiusSearch_falseWhenRadiusKmIsNull() {
        var params = new ListingSearchParams("Kerkstraat", "13", null, null, "Amsterdam", null, null, null, null, null, null);
        assertThat(params.hasRadiusSearch()).isFalse();
    }

    @Test
    void hasRadiusSearch_falseWhenRadiusKmSetButNoStreetOrCity() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, 5);
        assertThat(params.hasRadiusSearch()).isFalse();
    }

    @Test
    void hasRadiusSearch_falseWhenRadiusKmSetButOnlyBlankStreet() {
        var params = new ListingSearchParams("  ", null, null, null, null, null, null, null, null, null, 5);
        assertThat(params.hasRadiusSearch()).isFalse();
    }

    // --- non-radius path does not touch geocoding ---

    @Test
    void findAll_streetSearchWithoutRadius_doesNotCallGeocodingService() {
        var params = new ListingSearchParams("Kerkstraat", null, null, null, null, null, null, null, null, null, null);
        when(listingRepository.findAll(any(Specification.class), eq(PAGEABLE)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, PAGEABLE);

        verifyNoInteractions(geocodingService);
    }

    @Test
    void findAll_emptyParams_doesNotCallGeocodingService() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null);
        when(listingRepository.findAll(eq(PAGEABLE)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, PAGEABLE);

        verifyNoInteractions(geocodingService);
    }

    @Test
    void findAll_blankStreetWithNonNullCity_delegatesToFindOrFetchCityNotGeocodeAddress() {
        // blank street means hasRadiusSearch() treats street as absent → resolveRadiusCenter
        // falls through to the city branch and calls findOrFetchCity instead of geocodeAddress
        var params = new ListingSearchParams("  ", null, null, null, "Amsterdam", null, null, null, null, null, 5);
        when(geocodingService.findOrFetchCity("Amsterdam")).thenReturn(Optional.empty());
        stubEmptyPage();

        service.findAll(params, PAGEABLE);

        verify(geocodingService).findOrFetchCity("Amsterdam");
        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
    }
}

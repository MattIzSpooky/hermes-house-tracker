package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceFindForChatTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private ListingMapper mapper;
    @Mock private GeocodingService geocodingService;

    @InjectMocks
    private ListingService service;

    /**
     * Creates a Listing with a fixed UUID and wires up the priceHistoryRepository and mapper
     * stubs so that {@code ListingService.toDto} will produce a {@code ListingDto} whose
     * {@code currentPrice} equals {@code price} (or {@code null} when {@code price} is null).
     */
    private ListingEntity listingWithPrice(Integer price) {
        UUID id = UUID.randomUUID();
        ListingEntity listing = new ListingEntity();
        listing.setId(id);

        if (price != null) {
            PriceHistoryEntryEntity entry = mock(PriceHistoryEntryEntity.class);
            when(entry.getPrice()).thenReturn(price);
            when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(id, "asking_price"))
                    .thenReturn(Optional.of(entry));
        } else {
            when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(id, "asking_price"))
                    .thenReturn(Optional.empty());
        }

        ListingDto dto = new ListingDto(id, null, null, null, null, null, null, null, null,
                null, null, price, null, null, null, null, null, null, null);
        when(mapper.toDto(listing, price)).thenReturn(dto);


        return listing;
    }

    private void stubSearchForChat(List<ListingEntity> results) {
        when(listingRepository.searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(results);
    }

    @Test
    void findForChat_minPrice_excludesListingsBelowMinPrice() {
        // Price filtering is now in SQL; mock returns what the DB would return after filtering.
        ListingEntity exact     = listingWithPrice(200_000);
        ListingEntity expensive = listingWithPrice(300_000);
        stubSearchForChat(List.of(exact, expensive));

        List<ListingDto> result = service.findForChat(200_000, null, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(dto -> dto.currentPrice() >= 200_000);
    }

    @Test
    void findForChat_maxPrice_excludesListingsAboveMaxPrice() {
        // Price filtering is now in SQL; mock returns what the DB would return after filtering.
        ListingEntity cheap = listingWithPrice(100_000);
        ListingEntity exact = listingWithPrice(200_000);
        stubSearchForChat(List.of(cheap, exact));

        List<ListingDto> result = service.findForChat(null, 200_000, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(dto -> dto.currentPrice() <= 200_000);
    }

    @Test
    void findForChat_nullCurrentPrice_excludedWhenMinPriceSet() {
        // SQL excludes listings with no price history when a minPrice is applied (NULL >= x is false).
        ListingEntity withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(withPrice));

        List<ListingDto> result = service.findForChat(100_000, null, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualTo(200_000);
    }

    @Test
    void findForChat_nullCurrentPrice_excludedWhenMaxPriceSet() {
        // SQL excludes listings with no price history when a maxPrice is applied (NULL <= x is false).
        ListingEntity withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(withPrice));

        List<ListingDto> result = service.findForChat(null, 300_000, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualTo(200_000);
    }

    @Test
    void findForChat_noPriceBounds_allListingsPassThrough() {
        ListingEntity a = listingWithPrice(100_000);
        ListingEntity b = listingWithPrice(200_000);
        stubSearchForChat(List.of(a, b));

        List<ListingDto> result = service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void findForChat_noPriceBounds_nullPriceListingsPassThrough() {
        // With no price bounds, SQL does not filter on price, so listings without price history are included.
        ListingEntity noPrice   = listingWithPrice(null);
        ListingEntity withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(noPrice, withPrice));

        List<ListingDto> result = service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null);

        assertThat(result).hasSize(2);
    }

    // ── findForChat: radius path (resolveLatLon) ──────────────────────────────

    @Test
    void findForChat_withNearAddressAndRadius_geocodesAndCallsNearLocationSearch() {
        GeocodeResult geocoded = new GeocodeResult(4.9041, 52.3676, null);
        when(geocodingService.geocodeAddress("Kerkstraat 13", "", "")).thenReturn(Optional.of(geocoded));
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000))).thenReturn(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, "Kerkstraat 13", null, 5);

        verify(geocodingService).geocodeAddress("Kerkstraat 13", "", "");
        verify(listingRepository).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000));
        verify(listingRepository, never()).searchForChat(
                any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void findForChat_withNearCityAndRadius_geocodesCityAndCallsNearLocationSearch() {
        CityEntity city = new CityEntity();
        city.setLatitude(52.3676);
        city.setLongitude(4.9041);
        when(geocodingService.findOrFetchCity("Amsterdam")).thenReturn(Optional.of(city));
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(10_000))).thenReturn(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, null, "Amsterdam", 10);

        verify(geocodingService).findOrFetchCity("Amsterdam");
        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
        verify(listingRepository).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(10_000));
    }

    @Test
    void findForChat_withNearAddressAndRadius_geocodingFails_fallsBackToRegularSearch() {
        when(geocodingService.geocodeAddress("Unknown Street", "", "")).thenReturn(Optional.empty());
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, "Unknown Street", null, 5);

        verify(listingRepository, never()).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyInt());
        verify(listingRepository).searchForChat(
                any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void findForChat_nearAddressBlank_resolvesFromNearCity() {
        CityEntity city = new CityEntity();
        city.setLatitude(52.3676);
        city.setLongitude(4.9041);
        when(geocodingService.findOrFetchCity("Amsterdam")).thenReturn(Optional.of(city));
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());

        // blank nearAddress → first branch of resolveLatLon is skipped → resolves via nearCity
        service.findForChat(null, null, null, null, null, null, null, null,
                false, "  ", "Amsterdam", 5);

        verify(geocodingService, never()).geocodeAddress(any(), any(), any());
        verify(geocodingService).findOrFetchCity("Amsterdam");
    }

    @Test
    void findForChat_radiusKmNull_nearAddressIgnored_usesRegularSearch() {
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, "Kerkstraat 13", null, null); // radiusKm=null → radius path skipped

        verifyNoInteractions(geocodingService);
        verify(listingRepository, never()).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void findForChat_radiusKmSetButBothNearAddressAndNearCityNull_usesRegularSearch() {
        // radiusKm != null but (null != null || null != null) is false → outer condition fails
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, null, null, 5);

        verifyNoInteractions(geocodingService);
        verify(listingRepository, never()).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void findForChat_blankNearAddressAndNullNearCity_resolveLatLonReturnsNull_usesRegularSearch() {
        // Reaches resolveLatLon(" ", null): nearAddress blank → skip; nearCity null → skip; return null
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, "  ", null, 5);

        verifyNoInteractions(geocodingService);
        verify(listingRepository, never()).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void findForChat_blankNearAddressAndBlankNearCity_resolveLatLonReturnsNull_usesRegularSearch() {
        // Reaches resolveLatLon(" ", "  "): both blank → both branches skipped → return null
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null,
                false, "  ", "  ", 5);

        verifyNoInteractions(geocodingService);
        verify(listingRepository, never()).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyInt());
    }
}

package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private PriceHistoryService priceHistoryService;
    @Mock private ListingMapper mapper;
    @Mock private GeocodingService geocodingService;

    @InjectMocks
    private ListingService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private ListingEntity entity(UUID id) {
        ListingEntity e = new ListingEntity();
        e.setId(id);
        return e;
    }

    private ListingDto dto(UUID id) {
        return new ListingDto(id, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private PriceHistoryEntryDto historyDto(int price) {
        return new PriceHistoryEntryDto(UUID.randomUUID(), price, "asking_price", null, null, null);
    }

    private PriceHistoryEntryEntity historyEntry(int price, String status) {
        PriceHistoryEntryEntity e = new PriceHistoryEntryEntity();
        e.setPrice(price);
        e.setStatus(status);
        return e;
    }

    /** Stubs the internal {@code toDto(ListingEntity)} call (no current price). */
    private void stubToDto(ListingEntity e, ListingDto dto) {
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(
                e.getId(), "asking_price")).thenReturn(Optional.empty());
        when(mapper.toDto(e, null)).thenReturn(dto);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedDto() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findById(id)).thenReturn(Optional.of(e));
        stubToDto(e, expected);

        assertThat(service.findById(id)).contains(expected);
    }

    @Test
    void findById_notFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(listingRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(id)).isEmpty();
        verifyNoInteractions(mapper);
    }

    // ── findByFundaId ─────────────────────────────────────────────────────────

    @Test
    void findByFundaId_found_returnsMappedDto() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findByFundaId("abc123")).thenReturn(Optional.of(e));
        stubToDto(e, expected);

        assertThat(service.findByFundaId("abc123")).contains(expected);
    }

    @Test
    void findByFundaId_notFound_returnsEmpty() {
        when(listingRepository.findByFundaId("missing")).thenReturn(Optional.empty());

        assertThat(service.findByFundaId("missing")).isEmpty();
    }

    // ── findAllActive ─────────────────────────────────────────────────────────

    @Test
    void findAllActive_delegatesAndMapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findAllByDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(e)));
        stubToDto(e, expected);

        Page<ListingDto> result = service.findAllActive(pageable);

        assertThat(result.getContent()).containsExactly(expected);
    }

    // ── findPriceHistoryByListingId ───────────────────────────────────────────

    @Test
    void findPriceHistoryByListingId_delegatesAndMapsEntries() {
        UUID listingId = UUID.randomUUID();
        PriceHistoryEntryEntity entry = historyEntry(350_000, "asking_price");
        PriceHistoryEntryDto entryDto = historyDto(350_000);
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId))
                .thenReturn(List.of(entry));
        when(mapper.toDto(entry)).thenReturn(entryDto);

        List<PriceHistoryEntryDto> result = service.findPriceHistoryByListingId(listingId);

        assertThat(result).containsExactly(entryDto);
    }

    @Test
    void findPriceHistoryByListingId_noEntries_returnsEmptyList() {
        UUID listingId = UUID.randomUUID();
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId))
                .thenReturn(List.of());

        assertThat(service.findPriceHistoryByListingId(listingId)).isEmpty();
    }

    // ── refreshAllPriceHistory ────────────────────────────────────────────────

    @Test
    void refreshAllPriceHistory_delegatesToPriceHistoryService() {
        service.refreshAllPriceHistory();

        verify(priceHistoryService).refreshAll();
    }

    // ── deleteAllDeleted ──────────────────────────────────────────────────────

    @Test
    void deleteAllDeleted_delegatesToRepository() {
        service.deleteAllDeleted();

        verify(listingRepository).deleteAllByDeletedAtIsNotNull();
    }

    // ── findByAddress ─────────────────────────────────────────────────────────

    @Test
    void findByAddress_withCity_foundByCity_returnsFirst() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                "Kerkstraat", "13", "Amsterdam")).thenReturn(List.of(e));
        stubToDto(e, expected);

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", "13", "Amsterdam");

        assertThat(result).contains(expected);
        verify(listingRepository, never()).findByStreetIgnoreCaseAndHouseNumberIgnoreCase(any(), any());
    }

    @Test
    void findByAddress_withCity_notFoundByCity_fallsBackToStreetAndNumber() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                "Kerkstraat", "13", "Amsterdam")).thenReturn(List.of());
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                "Kerkstraat", "13")).thenReturn(List.of(e));
        stubToDto(e, expected);

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", "13", "Amsterdam");

        assertThat(result).contains(expected);
    }

    @Test
    void findByAddress_withNullCity_skipsTripleKeyLookupAndUsesStreetAndNumber() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto expected = dto(id);
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                "Kerkstraat", "13")).thenReturn(List.of(e));
        stubToDto(e, expected);

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", "13", null);

        assertThat(result).contains(expected);
        verify(listingRepository, never())
                .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(any(), any(), any());
    }

    @Test
    void findByAddress_withBlankCity_treatedAsMissingCity() {
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase("Kerkstraat", "13"))
                .thenReturn(List.of());

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", "13", "  ");

        assertThat(result).isEmpty();
        verify(listingRepository, never())
                .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(any(), any(), any());
    }

    @Test
    void findByAddress_stripsLeadingAndTrailingWhitespaceFromInputs() {
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                "Kerkstraat", "13", "Amsterdam")).thenReturn(List.of());
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                "Kerkstraat", "13")).thenReturn(List.of());

        service.findByAddress("  Kerkstraat  ", "  13  ", "  Amsterdam  ");

        verify(listingRepository)
                .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                        "Kerkstraat", "13", "Amsterdam");
    }

    @Test
    void findByAddress_noMatchAtAll_returnsEmpty() {
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                any(), any(), any())).thenReturn(List.of());
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                any(), any())).thenReturn(List.of());

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", "13", "Amsterdam");

        assertThat(result).isEmpty();
    }

    @Test
    void findByAddress_nullStreet_passesNullToRepository() {
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                isNull(), eq("13"), eq("Amsterdam"))).thenReturn(List.of());
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                isNull(), eq("13"))).thenReturn(List.of());

        Optional<ListingDto> result = service.findByAddress(null, "13", "Amsterdam");

        assertThat(result).isEmpty();
        verify(listingRepository)
                .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(null, "13", "Amsterdam");
    }

    @Test
    void findByAddress_nullHouseNumber_passesNullToRepository() {
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
                eq("Kerkstraat"), isNull(), eq("Amsterdam"))).thenReturn(List.of());
        when(listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(
                eq("Kerkstraat"), isNull())).thenReturn(List.of());

        Optional<ListingDto> result = service.findByAddress("Kerkstraat", null, "Amsterdam");

        assertThat(result).isEmpty();
        verify(listingRepository)
                .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase("Kerkstraat", null, "Amsterdam");
    }

    // ── findPriceDropListings ─────────────────────────────────────────────────

    @Test
    void findPriceDropListings_noIdsFromRepository_returnsEmpty() {
        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of());
        when(listingRepository.findByIdIn(List.of())).thenReturn(List.of());

        assertThat(service.findPriceDropListings("Amsterdam", 5.0)).isEmpty();
    }

    @Test
    void findPriceDropListings_listingWithFewerThanTwoHistoryEntries_isExcluded() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of(id.toString()));
        when(listingRepository.findByIdIn(any())).thenReturn(List.of(e));
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id))
                .thenReturn(List.of(historyEntry(400_000, "asking_price"))); // only 1 entry

        assertThat(service.findPriceDropListings("Amsterdam", 5.0)).isEmpty();
    }

    @Test
    void findPriceDropListings_onlyNonAskingPriceEntries_isExcluded() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of(id.toString()));
        when(listingRepository.findByIdIn(any())).thenReturn(List.of(e));
        // Both entries have a different status → filtered out → history.size() < 2
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id)).thenReturn(List.of(
                historyEntry(400_000, "sold"),
                historyEntry(380_000, "sold")
        ));

        assertThat(service.findPriceDropListings("Amsterdam", 5.0)).isEmpty();
    }

    @Test
    void findPriceDropListings_withTwoAskingPriceEntries_returnsPriceDropResult() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto listingDto = dto(id);
        PriceHistoryEntryDto firstDto = historyDto(400_000);
        PriceHistoryEntryDto lastDto = historyDto(360_000);

        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of(id.toString()));
        when(listingRepository.findByIdIn(any())).thenReturn(List.of(e));
        PriceHistoryEntryEntity first = historyEntry(400_000, "asking_price");
        PriceHistoryEntryEntity last = historyEntry(360_000, "asking_price");
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id))
                .thenReturn(List.of(first, last));
        when(mapper.toDto(first)).thenReturn(firstDto);
        when(mapper.toDto(last)).thenReturn(lastDto);
        stubToDto(e, listingDto);

        List<PriceDropResult> results = service.findPriceDropListings("Amsterdam", 5.0);

        assertThat(results).hasSize(1);
        PriceDropResult result = results.get(0);
        assertThat(result.originalPrice()).isEqualTo(400_000);
        assertThat(result.currentPrice()).isEqualTo(360_000);
        assertThat(result.dropPercent()).isEqualTo(10.0);
        assertThat(result.listing()).isEqualTo(listingDto);
    }

    @Test
    void findPriceDropListings_mixedStatuses_onlyAskingPriceEntriesCountedForDrop() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        ListingDto listingDto = dto(id);
        PriceHistoryEntryDto firstDto = historyDto(500_000);
        PriceHistoryEntryDto lastDto = historyDto(450_000);

        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of(id.toString()));
        when(listingRepository.findByIdIn(any())).thenReturn(List.of(e));
        PriceHistoryEntryEntity asking1 = historyEntry(500_000, "asking_price");
        PriceHistoryEntryEntity sold = historyEntry(480_000, "sold"); // filtered out
        PriceHistoryEntryEntity asking2 = historyEntry(450_000, "asking_price");
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id))
                .thenReturn(List.of(asking1, sold, asking2));
        when(mapper.toDto(asking1)).thenReturn(firstDto);
        when(mapper.toDto(asking2)).thenReturn(lastDto);
        stubToDto(e, listingDto);

        List<PriceDropResult> results = service.findPriceDropListings("Amsterdam", 5.0);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).originalPrice()).isEqualTo(500_000);
        assertThat(results.get(0).currentPrice()).isEqualTo(450_000);
    }

    @Test
    void findPriceDropListings_multipleListings_sortedByDropPercentDescending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ListingEntity e1 = entity(id1);
        ListingEntity e2 = entity(id2);
        ListingDto dto1 = dto(id1);
        ListingDto dto2 = dto(id2);

        when(listingRepository.findListingIdsWithPriceDrop("Amsterdam", 5.0))
                .thenReturn(List.of(id1.toString(), id2.toString()));
        when(listingRepository.findByIdIn(any())).thenReturn(List.of(e1, e2));

        // e1: 5% drop
        PriceHistoryEntryEntity e1first = historyEntry(400_000, "asking_price");
        PriceHistoryEntryEntity e1last = historyEntry(380_000, "asking_price");
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id1))
                .thenReturn(List.of(e1first, e1last));
        when(mapper.toDto(e1first)).thenReturn(historyDto(400_000));
        when(mapper.toDto(e1last)).thenReturn(historyDto(380_000));
        stubToDto(e1, dto1);

        // e2: 20% drop
        PriceHistoryEntryEntity e2first = historyEntry(500_000, "asking_price");
        PriceHistoryEntryEntity e2last = historyEntry(400_000, "asking_price");
        when(priceHistoryRepository.findByListingIdOrderByTimestampAsc(id2))
                .thenReturn(List.of(e2first, e2last));
        when(mapper.toDto(e2first)).thenReturn(historyDto(500_000));
        when(mapper.toDto(e2last)).thenReturn(historyDto(400_000));
        stubToDto(e2, dto2);

        List<PriceDropResult> results = service.findPriceDropListings("Amsterdam", 5.0);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).dropPercent()).isGreaterThan(results.get(1).dropPercent());
        assertThat(results.get(0).listing()).isEqualTo(dto2); // 20% drop comes first
        assertThat(results.get(1).listing()).isEqualTo(dto1); // 5% drop comes second
    }
}

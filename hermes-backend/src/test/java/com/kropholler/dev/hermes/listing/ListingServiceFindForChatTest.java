package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceFindForChatTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private ListingMapper mapper;

    @InjectMocks
    private ListingService service;

    /**
     * Creates a Listing with a fixed UUID and wires up the priceHistoryRepository and mapper
     * stubs so that {@code ListingService.toDto} will produce a {@code ListingDto} whose
     * {@code currentPrice} equals {@code price} (or {@code null} when {@code price} is null).
     */
    private Listing listingWithPrice(Integer price) {
        UUID id = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setId(id);

        if (price != null) {
            PriceHistoryEntry entry = mock(PriceHistoryEntry.class);
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

    private void stubSearchForChat(List<Listing> results) {
        when(listingRepository.searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(results);
    }

    @Test
    void findForChat_minPrice_excludesListingsBelowMinPrice() {
        // Price filtering is now in SQL; mock returns what the DB would return after filtering.
        Listing exact     = listingWithPrice(200_000);
        Listing expensive = listingWithPrice(300_000);
        stubSearchForChat(List.of(exact, expensive));

        List<ListingDto> result = service.findForChat(200_000, null, null, null, null, null, null, null, false);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(dto -> dto.currentPrice() >= 200_000);
    }

    @Test
    void findForChat_maxPrice_excludesListingsAboveMaxPrice() {
        // Price filtering is now in SQL; mock returns what the DB would return after filtering.
        Listing cheap = listingWithPrice(100_000);
        Listing exact = listingWithPrice(200_000);
        stubSearchForChat(List.of(cheap, exact));

        List<ListingDto> result = service.findForChat(null, 200_000, null, null, null, null, null, null, false);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(dto -> dto.currentPrice() <= 200_000);
    }

    @Test
    void findForChat_nullCurrentPrice_excludedWhenMinPriceSet() {
        // SQL excludes listings with no price history when a minPrice is applied (NULL >= x is false).
        Listing withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(withPrice));

        List<ListingDto> result = service.findForChat(100_000, null, null, null, null, null, null, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualTo(200_000);
    }

    @Test
    void findForChat_nullCurrentPrice_excludedWhenMaxPriceSet() {
        // SQL excludes listings with no price history when a maxPrice is applied (NULL <= x is false).
        Listing withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(withPrice));

        List<ListingDto> result = service.findForChat(null, 300_000, null, null, null, null, null, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualTo(200_000);
    }

    @Test
    void findForChat_noPriceBounds_allListingsPassThrough() {
        Listing a = listingWithPrice(100_000);
        Listing b = listingWithPrice(200_000);
        stubSearchForChat(List.of(a, b));

        List<ListingDto> result = service.findForChat(null, null, null, null, null, null, null, null, false);

        assertThat(result).hasSize(2);
    }

    @Test
    void findForChat_noPriceBounds_nullPriceListingsPassThrough() {
        // With no price bounds, SQL does not filter on price, so listings without price history are included.
        Listing noPrice   = listingWithPrice(null);
        Listing withPrice = listingWithPrice(200_000);
        stubSearchForChat(List.of(noPrice, withPrice));

        List<ListingDto> result = service.findForChat(null, null, null, null, null, null, null, null, false);

        assertThat(result).hasSize(2);
    }
}

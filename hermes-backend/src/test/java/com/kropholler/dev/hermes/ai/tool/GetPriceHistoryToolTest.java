package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.tool.json.AddressParams;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPriceHistoryToolTest {

    @Mock ListingService listingService;

    private GetPriceHistoryTool tool() {
        return new GetPriceHistoryTool(listingService, new SimpleMeterRegistry());
    }

    private ListingDto dto(UUID id) {
        return new ListingDto(id, "f", "u", "Dorpstraat", "10", null, "1234AB",
            "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            280000, ListingStatus.FOR_SALE, null, 85, 4, 2, "B", null);
    }

    private PriceHistoryEntryDto priceEntry(int price) {
        return new PriceHistoryEntryDto(UUID.randomUUID(), price, "asking_price", "funda",
            LocalDate.of(2026, 1, 15), Instant.parse("2026-01-15T00:00:00Z"));
    }

    @Test
    void getPriceHistory_withHistory_returnsFormattedHistory() {
        UUID id = UUID.randomUUID();
        when(listingService.findByAddress("Dorpstraat", "10", "Utrecht")).thenReturn(Optional.of(dto(id)));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of(priceEntry(300000), priceEntry(280000)));

        String result = tool().getPriceHistory(new AddressParams("Dorpstraat", "10", "Utrecht"));

        assertThat(result).contains("Price history for Dorpstraat 10");
        assertThat(result).contains("300.000");
        assertThat(result).contains("280.000");
    }

    @Test
    void getPriceHistory_noAskingPriceEntries_returnsNoHistoryMessage() {
        UUID id = UUID.randomUUID();
        PriceHistoryEntryDto nonAsking = new PriceHistoryEntryDto(UUID.randomUUID(), 250000, "sold_price", "funda",
            LocalDate.now(), Instant.now());

        when(listingService.findByAddress("Dorpstraat", "10", "Utrecht")).thenReturn(Optional.of(dto(id)));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of(nonAsking));

        String result = tool().getPriceHistory(new AddressParams("Dorpstraat", "10", "Utrecht"));

        assertThat(result).contains("No price history found");
    }

    @Test
    void getPriceHistory_propertyNotFound_returnsNotFoundMessage() {
        when(listingService.findByAddress("Unknown", "1", "Nowhere")).thenReturn(Optional.empty());

        String result = tool().getPriceHistory(new AddressParams("Unknown", "1", "Nowhere"));

        assertThat(result).contains("Property not found");
    }
}

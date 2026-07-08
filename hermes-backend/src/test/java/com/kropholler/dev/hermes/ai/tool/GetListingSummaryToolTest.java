package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetListingSummaryToolTest {

    @Mock ListingService listingService;
    @Mock ListingSummaryService listingSummaryService;

    private GetListingSummaryTool tool() {
        return new GetListingSummaryTool(listingService, listingSummaryService, new SimpleMeterRegistry());
    }

    private ListingDto dto(UUID id, String description) {
        return new ListingDto(id, "f", "u", "Hoofdstraat", "7", null, "5000AB",
            "Tilburg", "Noord-Brabant", Instant.now(), Instant.now(),
            250000, ListingStatus.FOR_SALE, description, 75, 4, 2, "C", null, null);
    }

    @Test
    void getListingSummary_summaryRowExists_returnsSummary() {
        UUID id = UUID.randomUUID();
        when(listingService.findByAddress("Hoofdstraat", "7", "Tilburg")).thenReturn(Optional.of(dto(id, "raw desc")));
        when(listingSummaryService.findByListingId(id))
            .thenReturn(Optional.of(new ListingSummaryDto(id, "AI-generated summary.", Instant.now())));

        String result = tool().getListingSummary("Hoofdstraat", "7", "Tilburg");

        assertThat(result).isEqualTo("AI-generated summary.");
    }

    @Test
    void getListingSummary_noSummaryButDescriptionExists_returnsDescription() {
        UUID id = UUID.randomUUID();
        when(listingService.findByAddress("Hoofdstraat", "7", "Tilburg"))
            .thenReturn(Optional.of(dto(id, "Original listing description.")));
        when(listingSummaryService.findByListingId(id)).thenReturn(Optional.empty());

        String result = tool().getListingSummary("Hoofdstraat", "7", "Tilburg");

        assertThat(result).isEqualTo("Original listing description.");
    }

    @Test
    void getListingSummary_noSummaryNoDescription_returnsNotAvailableMessage() {
        UUID id = UUID.randomUUID();
        when(listingService.findByAddress("Hoofdstraat", "7", "Tilburg"))
            .thenReturn(Optional.of(dto(id, null)));
        when(listingSummaryService.findByListingId(id)).thenReturn(Optional.empty());

        String result = tool().getListingSummary("Hoofdstraat", "7", "Tilburg");

        assertThat(result).contains("No description is available");
    }

    @Test
    void getListingSummary_propertyNotFound_returnsNotFoundMessage() {
        when(listingService.findByAddress("Unknown", "1", "Nowhere")).thenReturn(Optional.empty());

        String result = tool().getListingSummary("Unknown", "1", "Nowhere");

        assertThat(result).contains("Property not found");
    }

    @Test
    void getListingSummary_noSummaryAndBlankDescription_returnsNotAvailableMessage() {
        // Covers L41 branch: description != null but isBlank() → false → "No description available"
        UUID id = UUID.randomUUID();
        when(listingService.findByAddress("Hoofdstraat", "7", "Tilburg"))
            .thenReturn(Optional.of(dto(id, "   ")));
        when(listingSummaryService.findByListingId(id)).thenReturn(Optional.empty());

        String result = tool().getListingSummary("Hoofdstraat", "7", "Tilburg");

        assertThat(result).contains("No description is available");
    }
}

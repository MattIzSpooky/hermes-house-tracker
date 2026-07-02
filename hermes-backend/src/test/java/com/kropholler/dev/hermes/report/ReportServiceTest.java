package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ListingService listingService;

    @InjectMocks
    private ReportService service;

    @Test
    void generateReport_computesPriceChange() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(30, ChronoUnit.DAYS), now, 380000, ListingStatus.FOR_SALE,
            null, null, null, null, null, null, null
        );

        List<PriceHistoryEntryDto> history = List.of(
            entry(now.minus(30, ChronoUnit.DAYS), 400000, "asking_price"),
            entry(now.minus(10, ChronoUnit.DAYS), 380000, "asking_price")
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId)).thenReturn(history);

        Optional<ListingReport> result = service.generateReport(listingId);

        assertThat(result).isPresent();
        assertThat(result.get().initialPrice()).isEqualTo(400000);
        assertThat(result.get().currentPrice()).isEqualTo(380000);
        assertThat(result.get().priceChangePct()).isEqualTo(-5.0);
        assertThat(result.get().priceHistory()).hasSize(2);
    }

    @Test
    void generateReport_returnsEmptyWhenListingNotFound() {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        assertThat(service.generateReport(id)).isEmpty();
    }

    @Test
    void generateReport_returnsEmptyWhenNoPriceHistory() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(5, ChronoUnit.DAYS), now, null, null,
            null, null, null, null, null, null, null
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId)).thenReturn(List.of());

        assertThat(service.generateReport(listingId)).isEmpty();
    }

    @Test
    void generateReport_currentStatusFromListing() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(10, ChronoUnit.DAYS), now, 400000, ListingStatus.SOLD,
            null, null, null, null, null, null, null
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId))
            .thenReturn(List.of(entry(now.minus(10, ChronoUnit.DAYS), 400000, "asking_price")));

        ListingReport report = service.generateReport(listingId).orElseThrow();

        assertThat(report.currentStatus()).isEqualTo(ListingStatus.SOLD);
    }

    private PriceHistoryEntryDto entry(Instant timestamp, int price, String status) {
        return new PriceHistoryEntryDto(UUID.randomUUID(), price, status, "walter",
            LocalDate.now().minusDays(10), timestamp);
    }
}

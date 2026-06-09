package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
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
            now.minus(30, ChronoUnit.DAYS), now, null
        );

        List<ListingSnapshotDto> snapshots = List.of(
            snapshot(now.minus(30, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(10, ChronoUnit.DAYS), 380000, ListingStatus.FOR_SALE)
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findSnapshotsByListingId(listingId)).thenReturn(snapshots);

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
    void generateReport_deduplicatesStatusHistory() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(60, ChronoUnit.DAYS), now, null
        );

        List<ListingSnapshotDto> snapshots = List.of(
            snapshot(now.minus(60, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(30, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(5,  ChronoUnit.DAYS), 400000, ListingStatus.UNDER_OFFER)
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findSnapshotsByListingId(listingId)).thenReturn(snapshots);

        ListingReport report = service.generateReport(listingId).orElseThrow();

        assertThat(report.statusHistory()).hasSize(2);
        assertThat(report.statusHistory().get(0).status()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(report.statusHistory().get(1).status()).isEqualTo(ListingStatus.UNDER_OFFER);
    }

    private ListingSnapshotDto snapshot(Instant scrapedAt, int price, ListingStatus status) {
        return new ListingSnapshotDto(
            UUID.randomUUID(), scrapedAt, price,
            75, 3, "A", LocalDate.now().minusDays(60), status
        );
    }
}

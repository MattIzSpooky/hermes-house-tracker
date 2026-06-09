package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ListingService listingService;

    @Transactional(readOnly = true)
    public Optional<ListingReport> generateReport(UUID listingId) {
        return listingService.findById(listingId).map(listing -> {
            List<ListingSnapshotDto> snapshots = listingService.findSnapshotsByListingId(listingId);

            if (snapshots.isEmpty()) return null;

            ListingSnapshotDto first = snapshots.get(0);
            ListingSnapshotDto latest = snapshots.get(snapshots.size() - 1);

            Integer initialPrice = first.askingPrice();
            Integer currentPrice = latest.askingPrice();

            Double priceChangePct = null;
            if (initialPrice != null && currentPrice != null && initialPrice != 0) {
                priceChangePct = Math.round(
                    ((currentPrice - initialPrice) / (double) initialPrice * 100) * 10.0) / 10.0;
            }

            Long daysListedOnFunda = snapshots.stream()
                .filter(s -> s.listedOnFundaSince() != null)
                .map(ListingSnapshotDto::listedOnFundaSince)
                .min(LocalDate::compareTo)
                .map(d -> ChronoUnit.DAYS.between(d, LocalDate.now()))
                .orElse(null);

            long daysInHermes = ChronoUnit.DAYS.between(
                listing.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now());

            List<PricePoint> priceHistory = snapshots.stream()
                .map(s -> new PricePoint(s.scrapedAt(), s.askingPrice()))
                .toList();

            List<StatusPoint> statusHistory = buildStatusHistory(snapshots);

            return new ListingReport(
                listingId, daysListedOnFunda, daysInHermes,
                currentPrice, initialPrice, priceChangePct,
                priceHistory, statusHistory, latest.status()
            );
        });
    }

    private List<StatusPoint> buildStatusHistory(List<ListingSnapshotDto> snapshots) {
        List<StatusPoint> history = new ArrayList<>();
        ListingStatus last = null;
        for (ListingSnapshotDto s : snapshots) {
            if (s.status() != null && !s.status().equals(last)) {
                history.add(new StatusPoint(s.scrapedAt(), s.status()));
                last = s.status();
            }
        }
        return history;
    }
}

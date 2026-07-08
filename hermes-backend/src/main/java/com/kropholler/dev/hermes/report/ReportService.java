package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ListingService listingService;

    @Transactional(readOnly = true)
    public Optional<ListingReport> generateReport(UUID listingId) {
        log.debug("generateReport called: listingId={}", listingId);
        Optional<ListingReport> report = listingService.findById(listingId).map(listing -> {
            List<PriceHistoryEntryDto> history = listingService.findPriceHistoryByListingId(listingId);

            List<PriceHistoryEntryDto> askingPrices = history.stream()
                .filter(e -> "asking_price".equals(e.status()))
                .toList();

            if (askingPrices.isEmpty()) {
                log.debug("generateReport: no asking-price history for listing {}", listingId);
                return null;
            }

            Integer initialPrice = askingPrices.getFirst().price();
            Integer currentPrice = askingPrices.getLast().price();

            Double priceChangePct = null;
            if (initialPrice != null && currentPrice != null && initialPrice != 0) {
                priceChangePct = Math.round(
                    ((currentPrice - initialPrice) / (double) initialPrice * 100) * 10.0) / 10.0;
            }

            long daysInHermes = ChronoUnit.DAYS.between(
                listing.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now());

            List<PricePoint> priceHistory = askingPrices.stream()
                .map(e -> new PricePoint(e.timestamp(), e.price()))
                .toList();

            return new ListingReport(
                listingId, daysInHermes,
                currentPrice, initialPrice, priceChangePct,
                priceHistory, listing.status()
            );
        });
        if (report.isEmpty()) {
            log.debug("generateReport: no listing or report data found for {}", listingId);
        }
        return report;
    }
}

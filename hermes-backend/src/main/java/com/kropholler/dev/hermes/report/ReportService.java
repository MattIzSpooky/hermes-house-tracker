package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
            List<PriceHistoryEntryDto> history = listingService.findPriceHistoryByListingId(listingId);

            List<PriceHistoryEntryDto> askingPrices = history.stream()
                .filter(e -> "asking_price".equals(e.status()))
                .toList();

            if (askingPrices.isEmpty()) return null;

            Integer initialPrice = askingPrices.get(0).price();
            Integer currentPrice = askingPrices.get(askingPrices.size() - 1).price();

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
    }
}

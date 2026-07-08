package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.tool.json.AddressParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

@Slf4j
public class GetPriceHistoryTool {
    private final ListingService listingService;
    private final Counter callCounter;

    public GetPriceHistoryTool(ListingService listingService, MeterRegistry meterRegistry) {
        this.listingService = listingService;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "getPriceHistory");
    }

    @Tool(description = "Get the full asking-price history for a specific property. "
            + "Call this when the user asks how the price has changed over time or whether a property got cheaper.")
    public String getPriceHistory(AddressParams params) {
        log.info("getPriceHistory called: street={}, houseNumber={}, city={}",
                params.street(), params.houseNumber(), params.city());
        callCounter.increment();
        return listingService.findByAddress(params.street(), params.houseNumber(), params.city())
                .map(dto -> {
                    List<PriceHistoryEntryDto> history = listingService.findPriceHistoryByListingId(dto.id())
                            .stream()
                            .filter(e -> "asking_price".equals(e.status()))
                            .toList();
                    log.debug("getPriceHistory found {} price entries for listing {}", history.size(), dto.id());
                    if (history.isEmpty()) return "No price history found for this property.";
                    StringBuilder sb = new StringBuilder("Price history for ")
                            .append(dto.street()).append(" ").append(dto.houseNumber())
                            .append(", ").append(dto.city()).append(":\n");
                    for (PriceHistoryEntryDto e : history) {
                        sb.append("- ").append(e.timestamp().toString(), 0, 10)
                                .append(": €").append(String.format("%,d", e.price()).replace(",", ".")
                                ).append("\n");
                    }
                    return sb.toString();
                })
                .orElseGet(() -> {
                    log.warn("getPriceHistory found no listing for street={}, houseNumber={}, city={}",
                            params.street(), params.houseNumber(), params.city());
                    return "Property not found. Call searchListings to locate the property first.";
                });
    }
}

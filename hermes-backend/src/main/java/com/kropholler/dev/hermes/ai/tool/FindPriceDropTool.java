package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.json.PriceDropParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceDropResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class FindPriceDropTool {
    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;
    private final Counter callCounter;

    public FindPriceDropTool(ListingService listingService,
                              ChatListingCardMapper mapper,
                              AtomicReference<List<ChatListingCard>> resultHolder,
                              MeterRegistry meterRegistry) {
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "findPriceDrop");
    }

    @Tool(description = "Find properties whose asking price has dropped since they were first tracked. "
            + "Call this when the user asks about price reductions, bargains, or properties that got cheaper. "
            + "Returns up to 5 listings with the largest percentage price drops.")
    public String findPriceDrop(PriceDropParams params) {
        String city = params.city() != null && !params.city().isBlank() ? params.city().strip() : null;
        double minDrop = params.minDropPercent() != null ? params.minDropPercent() : 1.0;
        log.info("findPriceDrop called: city={}, minDropPercent={}", city, minDrop);
        callCounter.increment();

        List<PriceDropResult> results = listingService.findPriceDropListings(city, minDrop);
        resultHolder.set(results.stream().map(r -> mapper.toChatListingCard(r.listing())).toList());

        if (results.isEmpty()) {
            return "No listings found with a price drop of at least " + minDrop + "%" +
                    (city != null ? " in " + city : "") + ".";
        }

        StringBuilder sb = new StringBuilder("Found ").append(results.size())
                .append(" listing(s) with price drops:\n\n");
        for (PriceDropResult r : results) {
            sb.append("- ").append(r.listing().street()).append(" ").append(r.listing().houseNumber());
            if (r.listing().houseNumberAddition() != null) sb.append(r.listing().houseNumberAddition());
            sb.append(", ").append(r.listing().city())
                    .append(" — dropped ").append(String.format("%.1f", r.dropPercent())).append("%")
                    .append(" (€").append(String.format("%,d", r.originalPrice()).replace(",", "."))
                    .append(" → €").append(String.format("%,d", r.currentPrice()).replace(",", "."))
                    .append(")\n");
        }
        return sb.toString();
    }
}

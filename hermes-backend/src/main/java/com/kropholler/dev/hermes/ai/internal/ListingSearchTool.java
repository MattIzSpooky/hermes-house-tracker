package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.ai.ChatListingCard;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI tool that searches for property listings.
 * Must be instantiated fresh per chat request — a single instance must not
 * be shared across concurrent requests because {@code resultHolder} is written
 * once per call and read by the caller after streaming completes.
 */
@Slf4j
public class ListingSearchTool {

    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;
    private final Counter callCounter;

    public ListingSearchTool(ListingService listingService,
                              ChatListingCardMapper mapper,
                              AtomicReference<List<ChatListingCard>> resultHolder,
                              MeterRegistry meterRegistry) {
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "searchListings");
    }

    @Tool(description = "Search for property listings matching the user's criteria. "
            + "ALWAYS call this tool before describing any listings — never invent property details. "
            + "Use priceSort='desc' for 'most expensive'/'highest price'/'luxury'; use priceSort='asc' or omit for 'cheapest'/'lowest price' or no sort preference.")
    public List<ChatListingCard> searchListings(
            @ToolParam(required = false, description = "City to filter by, omit if not specified") String city,
            @ToolParam(required = false, description = "Province to filter by, omit if not specified") String province,
            @ToolParam(required = false, description = "Minimum asking price in euros, omit if no minimum") Integer minPrice,
            @ToolParam(required = false, description = "Maximum asking price in euros, omit if no maximum") Integer maxPrice,
            @ToolParam(required = false, description = "Minimum number of bedrooms, omit if no minimum") Integer minBedrooms,
            @ToolParam(required = false, description = "Minimum total number of rooms, omit if no minimum") Integer minRooms,
            @ToolParam(required = false, description = "Minimum living area in square metres, omit if no minimum") Integer minLivingAreaM2,
            @ToolParam(required = false, description = "Free-text keywords to search in property descriptions, omit if not specified") String keywords,
            @ToolParam(required = false, description = "Price sort: use 'desc' for most expensive first, 'asc' or omit for cheapest first or no preference") String priceSort
    ) {
        boolean sortDesc = "desc".equalsIgnoreCase(priceSort);
        log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}",
                city, province, minBedrooms, minPrice, maxPrice, priceSort);
        callCounter.increment();
        List<ChatListingCard> cards = listingService.findForChat(
                minPrice, maxPrice,
                minBedrooms, minRooms, minLivingAreaM2,
                blankToNull(province), blankToNull(city), blankToNull(keywords),
                sortDesc
        ).stream().map(mapper::toChatListingCard).toList();
        log.info("searchListings returned {} results", cards.size());
        resultHolder.set(cards);
        return cards;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

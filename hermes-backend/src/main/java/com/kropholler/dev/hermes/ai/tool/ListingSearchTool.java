package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingChatSearchCriteria;
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
            + "Use priceSort='desc' for 'most expensive'/'highest price'/'luxury'; use priceSort='asc' or omit for 'cheapest'/'lowest price' or no sort preference. "
            + "For radius searches: set nearAddress (format: 'houseNumber, street, city') or nearCity and radiusKm. "
            + "Use limit to control how many results come back (default 5, max 15) — e.g. set limit=10 if the user asks for 10 listings.")
    @SuppressWarnings("java:S107") // flat scalar params required so Spring AI's tool schema has no wrapper object — see ListingSearchTool javadoc
    public List<ChatListingCard> searchListings(
            @ToolParam(required = false, description = "City to filter by, omit if not specified") String city,
            @ToolParam(required = false, description = "Province to filter by, omit if not specified") String province,
            @ToolParam(required = false, description = "Minimum asking price in euros, omit if no minimum") Integer minPrice,
            @ToolParam(required = false, description = "Maximum asking price in euros, omit if no maximum") Integer maxPrice,
            @ToolParam(required = false, description = "Minimum number of bedrooms, omit if no minimum") Integer minBedrooms,
            @ToolParam(required = false, description = "Minimum total number of rooms, omit if no minimum") Integer minRooms,
            @ToolParam(required = false, description = "Minimum living area in square metres, omit if no minimum") Integer minLivingAreaM2,
            @ToolParam(required = false, description = "Free-text keywords to search in property descriptions, omit if not specified") String keywords,
            @ToolParam(required = false, description = "Price sort: use 'desc' for most expensive first, 'asc' or omit for cheapest first or no preference") String priceSort,
            @ToolParam(required = false, description = "Address to search near, format: 'houseNumber, street, city'. Use when user asks about listings near a specific address.") String nearAddress,
            @ToolParam(required = false, description = "City name to search near. Use when user asks about listings near a city.") String nearCity,
            @ToolParam(required = false, description = "Search radius in kilometres. Required when nearAddress or nearCity is set.") Integer radiusKm,
            @ToolParam(required = false, description = "Number of listings to return, default 5, max 15") Integer limit) {
        boolean sortDesc = "desc".equalsIgnoreCase(priceSort);
        log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}, nearAddress={}, nearCity={}, radiusKm={}, limit={}",
                city, province, minBedrooms, minPrice, maxPrice, priceSort, nearAddress, nearCity, radiusKm, limit);
        callCounter.increment();
        List<ChatListingCard> cards = listingService.findForChat(ListingChatSearchCriteria.builder()
                .minPrice(minPrice).maxPrice(maxPrice)
                .minBedrooms(minBedrooms).minRooms(minRooms)
                .minLivingAreaM2(minLivingAreaM2)
                .province(blankToNull(province)).city(blankToNull(city))
                .keywords(blankToNull(keywords))
                .sortByPriceDesc(sortDesc)
                .nearAddress(blankToNull(nearAddress)).nearCity(blankToNull(nearCity))
                .radiusKm(radiusKm).limit(limit)
                .build()
        ).stream().map(mapper::toChatListingCard).toList();
        log.info("searchListings returned {} results", cards.size());
        resultHolder.set(cards);
        return cards;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

package com.kropholler.dev.hermes.ai.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kropholler.dev.hermes.ai.ChatListingCard;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

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

    /**
     * Sort direction for price ordering. Must always be set explicitly — never omit this field.
     * Use DESC for most expensive / highest price. Use ASC for cheapest / lowest price or when
     * the user has not expressed a price sort preference.
     */
    public enum SortOrder { ASC, DESC }

    public record SearchParams(
            @JsonPropertyDescription("Minimum asking price in euros, null if no minimum")
            Integer minPrice,
            @JsonPropertyDescription("Maximum asking price in euros, null if no maximum")
            Integer maxPrice,
            @JsonPropertyDescription("Minimum number of bedrooms, null if no minimum")
            Integer minBedrooms,
            @JsonPropertyDescription("Minimum total number of rooms, null if no minimum")
            Integer minRooms,
            @JsonPropertyDescription("Minimum living area in square metres, null if no minimum")
            Integer minLivingAreaM2,
            @JsonPropertyDescription("Province to filter by, null if not specified")
            String province,
            @JsonPropertyDescription("City to filter by, null if not specified")
            String city,
            @JsonPropertyDescription("Free-text keywords to search in property descriptions, null if not specified")
            String keywords,
            @JsonPropertyDescription("Price sort direction. Set to DESC when user asks for 'most expensive', 'highest price', 'priciest', 'luxury', or 'most valuable'. Set to ASC (default) for 'cheapest', 'lowest price', or when no sort preference is mentioned. NEVER omit this field.")
            SortOrder priceSort
    ) {
        public SearchParams {
            if (priceSort == null) priceSort = SortOrder.ASC;
        }
    }

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
            + "Set priceSort=DESC for 'most expensive'/'highest price'/'luxury'; set priceSort=ASC for 'cheapest'/'lowest price' or no preference.")
    public List<ChatListingCard> searchListings(SearchParams params) {
        log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}",
                params.city(), params.province(), params.minBedrooms(), params.minPrice(), params.maxPrice(), params.priceSort());
        callCounter.increment();
        List<ChatListingCard> cards = listingService.findForChat(
                params.minPrice(), params.maxPrice(),
                params.minBedrooms(), params.minRooms(), params.minLivingAreaM2(),
                blankToNull(params.province()), blankToNull(params.city()), blankToNull(params.keywords()),
                params.priceSort() == SortOrder.DESC
        ).stream().map(mapper::toChatListingCard).toList();
        log.info("searchListings returned {} results", cards.size());
        resultHolder.set(cards);
        return cards;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

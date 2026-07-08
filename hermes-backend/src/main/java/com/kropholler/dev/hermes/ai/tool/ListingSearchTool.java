package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.json.ListingSearchToolParams;
import com.kropholler.dev.hermes.listing.ListingChatSearchCriteria;
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
    public List<ChatListingCard> searchListings(ListingSearchToolParams params) {
        boolean sortDesc = "desc".equalsIgnoreCase(params.priceSort());
        log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}, nearAddress={}, nearCity={}, radiusKm={}, limit={}",
                params.city(), params.province(), params.minBedrooms(), params.minPrice(), params.maxPrice(),
                params.priceSort(), params.nearAddress(), params.nearCity(), params.radiusKm(), params.limit());
        callCounter.increment();
        List<ChatListingCard> cards = listingService.findForChat(ListingChatSearchCriteria.builder()
                .minPrice(params.minPrice()).maxPrice(params.maxPrice())
                .minBedrooms(params.minBedrooms()).minRooms(params.minRooms())
                .minLivingAreaM2(params.minLivingAreaM2())
                .province(blankToNull(params.province())).city(blankToNull(params.city()))
                .keywords(blankToNull(params.keywords()))
                .sortByPriceDesc(sortDesc)
                .nearAddress(blankToNull(params.nearAddress())).nearCity(blankToNull(params.nearCity()))
                .radiusKm(params.radiusKm()).limit(params.limit())
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

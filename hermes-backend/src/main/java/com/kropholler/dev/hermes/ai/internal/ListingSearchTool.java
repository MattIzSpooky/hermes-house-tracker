package com.kropholler.dev.hermes.ai.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kropholler.dev.hermes.ai.ChatListingCard;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingService;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI tool that searches for property listings.
 * Must be instantiated fresh per chat request — a single instance must not
 * be shared across concurrent requests because {@code resultHolder} is written
 * once per call and read by the caller after streaming completes.
 */
public class ListingSearchTool {

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
            String keywords
    ) {}

    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;

    public ListingSearchTool(ListingService listingService,
                              ChatListingCardMapper mapper,
                              AtomicReference<List<ChatListingCard>> resultHolder) {
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
    }

    @Tool(description = "Search for property listings matching the user's criteria. "
            + "Call this whenever the user expresses a housing preference or asks to see listings.")
    public List<ChatListingCard> searchListings(SearchParams params) {
        List<ChatListingCard> cards = listingService.findForChat(
                params.minPrice(), params.maxPrice(),
                params.minBedrooms(), params.minRooms(), params.minLivingAreaM2(),
                params.province(), params.city(), params.keywords()
        ).stream().map(mapper::toChatListingCard).toList();
        resultHolder.set(cards);
        return cards;
    }
}

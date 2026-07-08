package com.kropholler.dev.hermes.ai.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ListingSearchToolParams(
        @JsonPropertyDescription("City to filter by, omit if not specified")
        String city,
        @JsonPropertyDescription("Province to filter by, omit if not specified")
        String province,
        @JsonPropertyDescription("Minimum asking price in euros, omit if no minimum")
        Integer minPrice,
        @JsonPropertyDescription("Maximum asking price in euros, omit if no maximum")
        Integer maxPrice,
        @JsonPropertyDescription("Minimum number of bedrooms, omit if no minimum")
        Integer minBedrooms,
        @JsonPropertyDescription("Minimum total number of rooms, omit if no minimum")
        Integer minRooms,
        @JsonPropertyDescription("Minimum living area in square metres, omit if no minimum")
        Integer minLivingAreaM2,
        @JsonPropertyDescription("Free-text keywords to search in property descriptions, omit if not specified")
        String keywords,
        @JsonPropertyDescription("Price sort: use 'desc' for most expensive first, 'asc' or omit for cheapest first or no preference")
        String priceSort,
        @JsonPropertyDescription("Address to search near, format: 'houseNumber, street, city'. Use when user asks about listings near a specific address.")
        String nearAddress,
        @JsonPropertyDescription("City name to search near. Use when user asks about listings near a city.")
        String nearCity,
        @JsonPropertyDescription("Search radius in kilometres. Required when nearAddress or nearCity is set.")
        Integer radiusKm,
        @JsonPropertyDescription("Number of listings to return, default 5, max 15")
        Integer limit
) {
}

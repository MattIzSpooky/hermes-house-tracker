package com.kropholler.dev.hermes.ai.agent.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SaveAreaResearchToolParams(
        @JsonPropertyDescription("Friendly name for this search, e.g. 'Best homes near me'")
        String name,
        @JsonPropertyDescription("Search radius in kilometres from the target location")
        Integer radiusKm,
        @JsonPropertyDescription("Number of listings to rank, default 5, max 15")
        Integer limit,
        @JsonPropertyDescription("Minimum number of bedrooms")
        Integer minBedrooms,
        @JsonPropertyDescription("Minimum total rooms")
        Integer minRooms,
        @JsonPropertyDescription("Minimum living area in square metres")
        Integer minLivingAreaM2,
        @JsonPropertyDescription("Minimum asking price in euros")
        Integer minPrice,
        @JsonPropertyDescription("Maximum asking price in euros")
        Integer maxPrice,
        @JsonPropertyDescription("Keywords to search in descriptions")
        String keywords,
        @JsonPropertyDescription("Address to search near instead of the user's home address, format: 'houseNumber, street, city'")
        String nearAddress,
        @JsonPropertyDescription("City to search near instead of the user's home address")
        String nearCity
) {
}

package com.kropholler.dev.hermes.ai.agent.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SaveWatchToolParams(
        @JsonPropertyDescription("Friendly name for this watch, e.g. 'Utrecht 3-bed under 400k'")
        String name,
        @JsonPropertyDescription("City to filter by")
        String city,
        @JsonPropertyDescription("Province to filter by")
        String province,
        @JsonPropertyDescription("Minimum asking price in euros")
        Integer minPrice,
        @JsonPropertyDescription("Maximum asking price in euros")
        Integer maxPrice,
        @JsonPropertyDescription("Minimum number of bedrooms")
        Integer minBedrooms,
        @JsonPropertyDescription("Minimum total rooms")
        Integer minRooms,
        @JsonPropertyDescription("Minimum living area in square metres")
        Integer minLivingAreaM2,
        @JsonPropertyDescription("Keywords to search in descriptions")
        String keywords,
        @JsonPropertyDescription("City to search near")
        String nearCity,
        @JsonPropertyDescription("Radius in km when nearCity is set")
        Integer radiusKm
) {
}

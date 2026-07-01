package com.kropholler.dev.hermes.ai.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record PriceDropParams(
        @JsonPropertyDescription("City to filter by, null to search all cities")
        String city,
        @JsonPropertyDescription("Minimum price drop percentage (e.g. 5.0 for a 5% drop). Defaults to 1.0 if not specified.")
        Double minDropPercent
) {}

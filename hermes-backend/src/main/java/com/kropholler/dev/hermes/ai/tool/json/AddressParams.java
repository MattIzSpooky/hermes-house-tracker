package com.kropholler.dev.hermes.ai.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AddressParams(
        @JsonPropertyDescription("Street name of the property, e.g. 'Herenstraat'")
        String street,
        @JsonPropertyDescription("House number of the property, e.g. '160'")
        String houseNumber,
        @JsonPropertyDescription("City where the property is located, e.g. 'Weert'")
        String city
) {}

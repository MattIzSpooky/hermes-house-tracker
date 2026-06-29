package com.kropholler.dev.hermes.ai.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AddressEntry(
        @JsonPropertyDescription("Street name, e.g. 'Herenstraat'") String street,
        @JsonPropertyDescription("House number, e.g. '160'") String houseNumber,
        @JsonPropertyDescription("City, e.g. 'Weert'") String city
) {}

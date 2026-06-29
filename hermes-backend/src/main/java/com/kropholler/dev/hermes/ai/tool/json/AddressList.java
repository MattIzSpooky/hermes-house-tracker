package com.kropholler.dev.hermes.ai.tool.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record AddressList(
        @JsonPropertyDescription("List of properties to compare, each with street, houseNumber, and city")
        List<AddressEntry> addresses
) {}


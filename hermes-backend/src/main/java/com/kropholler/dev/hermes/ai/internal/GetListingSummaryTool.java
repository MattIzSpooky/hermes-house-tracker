package com.kropholler.dev.hermes.ai.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Spring AI tool that retrieves an AI-generated summary for a specific property by address.
 * Must be instantiated fresh per chat request — not thread-safe across concurrent requests.
 */
@Slf4j
public class GetListingSummaryTool {

    public record AddressParams(
            @JsonPropertyDescription("Street name of the property, e.g. 'Herenstraat'")
            String street,
            @JsonPropertyDescription("House number of the property, e.g. '160'")
            String houseNumber,
            @JsonPropertyDescription("City where the property is located, e.g. 'Weert'")
            String city
    ) {}

    private final ListingService listingService;
    private final ListingSummaryService listingSummaryService;
    private final Counter callCounter;

    public GetListingSummaryTool(ListingService listingService,
                                  ListingSummaryService listingSummaryService,
                                  MeterRegistry meterRegistry) {
        this.listingService = listingService;
        this.listingSummaryService = listingSummaryService;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "getListingSummary");
    }

    @Tool(description = "Get a summary or description for a specific property by its street address. "
            + "Call this when the user asks for a summary or description of a specific listing. "
            + "Use the street name, house number, and city of the property — these are available from a previous searchListings result. "
            + "If you don't yet know the address, call searchListings first.")
    public String getListingSummary(AddressParams params) {
        log.info("getListingSummary called: street={}, houseNumber={}, city={}",
                params.street(), params.houseNumber(), params.city());
        callCounter.increment();
        return listingService.findByAddress(params.street(), params.houseNumber(), params.city())
                .map(dto -> listingSummaryService.findByListingId(dto.id())
                        .map(summary -> summary.summary())
                        .orElseGet(() -> dto.description() != null && !dto.description().isBlank()
                                ? dto.description()
                                : "No description is available for this property yet."))
                .orElse("Property not found at this address. Call searchListings to locate the property first.");
    }
}

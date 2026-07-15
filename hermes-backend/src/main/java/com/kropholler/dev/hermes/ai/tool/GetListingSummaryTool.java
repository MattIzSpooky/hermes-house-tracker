package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.exception.NotFoundException;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Spring AI tool that retrieves an AI-generated summary for a specific property by address.
 * Must be instantiated fresh per chat request — not thread-safe across concurrent requests.
 */
@Slf4j
public class GetListingSummaryTool {
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
    public String getListingSummary(
            @ToolParam(description = "Street name of the property, e.g. 'Herenstraat'") String street,
            @ToolParam(description = "House number of the property, e.g. '160'") String houseNumber,
            @ToolParam(description = "City where the property is located, e.g. 'Weert'") String city) {
        log.info("getListingSummary called: street={}, houseNumber={}, city={}", street, houseNumber, city);
        callCounter.increment();
        return listingService.findByAddress(street, houseNumber, city)
                .map(dto -> {
                    log.debug("getListingSummary resolved listing {} for address", dto.id());
                    try {
                        return listingSummaryService.findByListingId(dto.id()).summary();
                    } catch (NotFoundException e) {
                        return dto.description() != null && !dto.description().isBlank()
                                ? dto.description()
                                : "No description is available for this property yet.";
                    }
                })
                .orElseGet(() -> {
                    log.warn("getListingSummary found no listing for street={}, houseNumber={}, city={}",
                            street, houseNumber, city);
                    return "Property not found at this address. Call searchListings to locate the property first.";
                });
    }
}

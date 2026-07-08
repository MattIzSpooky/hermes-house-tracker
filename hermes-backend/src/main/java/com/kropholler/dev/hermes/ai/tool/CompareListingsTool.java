package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.json.AddressEntry;
import com.kropholler.dev.hermes.ai.tool.json.AddressList;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CompareListingsTool {
    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;
    private final Counter callCounter;

    public CompareListingsTool(ListingService listingService,
                                ChatListingCardMapper mapper,
                                AtomicReference<List<ChatListingCard>> resultHolder,
                                MeterRegistry meterRegistry) {
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "compareListings");
    }

    @Tool(description = "Compare two or more specific properties side by side. "
            + "Call this when the user wants to compare listings they have already discussed or searched for. "
            + "Provide the address of each property to compare.")
    public String compareListings(AddressList params) {
        log.info("compareListings called for {} addresses", params.addresses().size());
        callCounter.increment();

        List<ListingDto> found = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (AddressEntry a : params.addresses()) {
            Optional<ListingDto> dto = listingService.findByAddress(a.street(), a.houseNumber(), a.city());
            if (dto.isPresent()) {
                found.add(dto.get());
            } else {
                notFound.add(a.street() + " " + a.houseNumber() + ", " + a.city());
            }
        }

        resultHolder.set(found.stream().map(mapper::toChatListingCard).toList());
        log.info("compareListings resolved {} of {} addresses ({} not found)",
                found.size(), params.addresses().size(), notFound.size());

        if (found.isEmpty()) return "None of the requested properties were found in the database.";

        StringBuilder sb = new StringBuilder("Comparison of ").append(found.size()).append(" properties:\n\n");
        for (ListingDto dto : found) {
            sb.append("### ").append(dto.street()).append(" ").append(dto.houseNumber());
            if (dto.houseNumberAddition() != null) sb.append(dto.houseNumberAddition());
            sb.append(", ").append(dto.city()).append("\n");
            sb.append("- Price: ").append(dto.currentPrice() != null ? "€" + String.format("%,d", dto.currentPrice()).replace(",", ".") : "unknown").append("\n");
            sb.append("- Bedrooms: ").append(dto.bedrooms() != null ? dto.bedrooms() : "unknown").append("\n");
            sb.append("- Rooms: ").append(dto.rooms() != null ? dto.rooms() : "unknown").append("\n");
            sb.append("- Living area: ").append(dto.livingAreaM2() != null ? dto.livingAreaM2() + " m²" : "unknown").append("\n");
            sb.append("- Plot area: ").append(dto.plotAreaM2() != null ? dto.plotAreaM2() + " m²" : "unknown").append("\n");
            sb.append("- Energy label: ").append(dto.energyLabel() != null ? dto.energyLabel() : "unknown").append("\n");
            sb.append("- Status: ").append(dto.status() != null ? dto.status() : "unknown").append("\n\n");
        }
        if (!notFound.isEmpty()) {
            sb.append("Not found: ").append(String.join(", ", notFound)).append("\n");
        }
        return sb.toString();
    }
}

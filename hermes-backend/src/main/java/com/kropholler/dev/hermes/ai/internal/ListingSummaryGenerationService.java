package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingSnapshotsCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSummaryGenerationService {

    private final ListingSummaryRepository summaryRepository;
    private final ListingService listingService;
    private final ChatClient.Builder chatClientBuilder;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingSnapshotsCreated(ListingSnapshotsCreated event) {
        ChatClient chatClient = chatClientBuilder.build();

        for (UUID listingId : event.listingIds()) {
            listingService.findById(listingId).ifPresent(listing -> {
                String summary = generateSummary(chatClient, listing);
                upsertSummary(listingId, summary);
            });
        }
    }

    private String generateSummary(ChatClient chatClient, ListingDto listing) {
        ListingSnapshotDto snapshot = listing.latestSnapshot();
        String prompt = buildPrompt(listing, snapshot);
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("Failed to generate AI summary for listing {}", listing.id(), e);
            return "Summary not available.";
        }
    }

    private String buildPrompt(ListingDto listing, ListingSnapshotDto snapshot) {
        return String.format(
            """
            Write a concise, plain-language summary (2-3 sentences) of this Dutch property listing.
            Include the key selling points, price, and location.

            Address: %s %s%s, %s %s, %s
            Price: €%,d
            Size: %d m²
            Rooms: %d
            Energy label: %s
            Status: %s
            """,
            listing.street(), listing.houseNumber(),
            listing.houseNumberAddition() != null ? " " + listing.houseNumberAddition() : "",
            listing.zipCode(), listing.city(), listing.province(),
            snapshot != null ? snapshot.askingPrice() : 0,
            snapshot != null ? snapshot.livingAreaM2() : 0,
            snapshot != null ? snapshot.rooms() : 0,
            snapshot != null && snapshot.energyLabel() != null ? snapshot.energyLabel() : "unknown",
            snapshot != null ? snapshot.status() : "unknown"
        );
    }

    private void upsertSummary(UUID listingId, String summaryText) {
        ListingSummary summary = summaryRepository.findByListingId(listingId)
            .orElseGet(() -> {
                ListingSummary s = new ListingSummary();
                s.setListingId(listingId);
                return s;
            });
        summary.setSummary(summaryText);
        summary.setGeneratedAt(Instant.now());
        summaryRepository.save(summary);
    }
}

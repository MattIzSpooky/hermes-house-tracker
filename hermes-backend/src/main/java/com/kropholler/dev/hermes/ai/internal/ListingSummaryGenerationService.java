package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.ai.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSummaryGenerationService {

    private final ListingSummaryRepository summaryRepository;
    private final ListingService listingService;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * Returns the existing summary for {@code listingId} if one exists, otherwise generates,
     * persists, and returns a new one. Safe to call on every page load — generation only runs once.
     */
    @Transactional
    public Optional<ListingSummaryDto> generateIfAbsent(UUID listingId) {
        if (summaryRepository.findByListingId(listingId).isPresent()) {
            return summaryRepository.findByListingId(listingId)
                    .map(s -> new ListingSummaryDto(s.getListingId(), s.getSummary(), s.getGeneratedAt()));
        }
        return listingService.findById(listingId).map(listing -> {
            log.info("Generating AI summary for listing {}", listingId);
            ChatClient chatClient = chatClientBuilder.build();
            String text = generateSummary(chatClient, listing);
            upsertSummary(listingId, text);
            return new ListingSummaryDto(listingId, text, Instant.now());
        });
    }

    private String generateSummary(ChatClient chatClient, ListingDto listing) {
        String prompt = buildPrompt(listing);
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("Failed to generate AI summary for listing {}", listing.id(), e);
            return "Summary not available.";
        }
    }

    private String buildPrompt(ListingDto listing) {
        return String.format(
            """
            Write a concise, plain-language summary (2-3 sentences) of this Dutch property listing.
            Include the key selling points, price, and location.

            Address: %s %s%s, %s %s, %s
            Price: €%,d
            Status: %s
            """,
            listing.street(), listing.houseNumber(),
            listing.houseNumberAddition() != null ? " " + listing.houseNumberAddition() : "",
            listing.zipCode(), listing.city(), listing.province(),
            listing.currentPrice() != null ? listing.currentPrice() : 0,
            listing.status() != null ? listing.status() : "unknown"
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

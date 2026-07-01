package com.kropholler.dev.hermes.listing.summary;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSummaryGenerationService {

    private final ListingSummaryRepository summaryRepository;
    private final ListingService listingService;
    private final ChatClient.Builder chatClientBuilder;

    @Transactional
    public void generate(UUID listingId) {
        listingService.findById(listingId).ifPresentOrElse(listing -> {
            log.info("Generating AI summary for listing {}", listingId);
            List<PriceHistoryEntryDto> priceHistory = listingService.findPriceHistoryByListingId(listingId)
                    .stream().filter(e -> "asking_price".equals(e.status())).toList();
            String text = callLlm(listing, priceHistory);
            upsertSummary(listingId, text);
            log.info("AI summary saved for listing {}", listingId);
        }, () -> log.warn("Cannot generate summary — listing {} not found", listingId));
    }

    private String callLlm(ListingDto listing, List<PriceHistoryEntryDto> priceHistory) {
        try {
            return chatClientBuilder.build()
                    .prompt()
                    .user(buildPrompt(listing, priceHistory))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM call failed for listing {}", listing.id(), e);
            return "Summary could not be generated.";
        }
    }

    private String buildPrompt(ListingDto listing, List<PriceHistoryEntryDto> priceHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write a concise, plain-language summary (3-5 sentences) of this Dutch property listing. ")
          .append("Highlight the most notable features, the price, and the location. ")
          .append("Write in English.\n\n");

        sb.append("Address: ").append(listing.street()).append(" ").append(listing.houseNumber());
        if (listing.houseNumberAddition() != null) sb.append(listing.houseNumberAddition());
        sb.append(", ").append(listing.zipCode()).append(" ").append(listing.city())
          .append(", ").append(listing.province()).append("\n");

        sb.append("Status: ").append(listing.status() != null ? listing.status() : "unknown").append("\n");

        if (listing.currentPrice() != null) {
            sb.append("Current asking price: €").append(String.format("%,d", listing.currentPrice()).replace(",", ".")).append("\n");
        }

        if (listing.livingAreaM2() != null) sb.append("Living area: ").append(listing.livingAreaM2()).append(" m²\n");
        if (listing.plotAreaM2() != null)   sb.append("Plot area: ").append(listing.plotAreaM2()).append(" m²\n");
        if (listing.rooms() != null)        sb.append("Rooms: ").append(listing.rooms()).append("\n");
        if (listing.bedrooms() != null)     sb.append("Bedrooms: ").append(listing.bedrooms()).append("\n");
        if (listing.energyLabel() != null)  sb.append("Energy label: ").append(listing.energyLabel()).append("\n");

        if (!priceHistory.isEmpty()) {
            sb.append("Price history:\n");
            for (PriceHistoryEntryDto e : priceHistory) {
                sb.append("  - ").append(e.timestamp().toString(), 0, 10)
                  .append(": €").append(e.price() != null ? String.format("%,d", e.price()).replace(",", ".") : "?")
                  .append("\n");
            }
        }

        if (listing.description() != null && !listing.description().isBlank()) {
            sb.append("Original listing description:\n").append(listing.description()).append("\n");
        }

        return sb.toString();
    }

    private void upsertSummary(UUID listingId, String summaryText) {
        ListingSummaryEntity summary = summaryRepository.findByListingId(listingId)
            .orElseGet(() -> {
                ListingSummaryEntity s = new ListingSummaryEntity();
                s.setListingId(listingId);
                return s;
            });
        summary.setSummary(summaryText);
        summary.setGeneratedAt(Instant.now());
        summaryRepository.save(summary);
    }
}

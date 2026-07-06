package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.CompareListingsTool;
import com.kropholler.dev.hermes.ai.tool.GetListingSummaryTool;
import com.kropholler.dev.hermes.ai.tool.GetPriceHistoryTool;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
class AreaResearchTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final UserProfileRepository userProfileRepository;

    public AreaResearchTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                                    ListingService listingService,
                                    ChatListingCardMapper chatListingCardMapper,
                                    ListingSummaryService listingSummaryService,
                                    MeterRegistry meterRegistry,
                                    ObjectMapper objectMapper,
                                    UserProfileRepository userProfileRepository) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.AREA_RESEARCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTaskEntity task) {
        AreaResearchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), AreaResearchPayload.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize AreaResearchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        double[] center = resolveCenter(task.getUserId(), payload);
        if (center == null) {
            log.warn("No center coordinates available for area research task {}; skipping this run", task.getId());
            return Optional.empty();
        }

        List<ListingDto> candidates = listingService.findNearLocation(
                center[0], center[1],
                payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
                null, payload.keywords(), payload.minPrice(), payload.maxPrice(),
                payload.radiusKm() * 1000, payload.limit());

        if (candidates.isEmpty()) {
            log.info("Area research task {}: no candidates found within radius", task.getId());
            return Optional.empty();
        }

        var summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool = new CompareListingsTool(listingService, chatListingCardMapper,
                new AtomicReference<>(List.of()), meterRegistry);

        String result = chatClient.prompt()
                .user(buildPrompt(payload, candidates))
                .tools(summaryTool, historyTool, compareTool)
                .call()
                .content();

        if (result == null || result.isBlank()) return Optional.empty();

        List<UUID> listingIds = candidates.stream().map(ListingDto::id).toList();
        return Optional.of(new NotificationContent(
                "%d best listings within %dkm".formatted(candidates.size(), payload.radiusKm()),
                result, listingIds));
    }

    private double[] resolveCenter(UUID userId, AreaResearchPayload payload) {
        if (payload.overrideLon() != null && payload.overrideLat() != null) {
            return new double[] { payload.overrideLon(), payload.overrideLat() };
        }
        return userProfileRepository.findById(userId)
                .filter(p -> p.getLongitude() != null && p.getLatitude() != null)
                .map(p -> new double[] { p.getLongitude(), p.getLatitude() })
                .orElse(null);
    }

    private String buildPrompt(AreaResearchPayload payload, List<ListingDto> candidates) {
        String candidateList = candidates.stream()
                .map(c -> "- %s %s, %s: €%s, %s bedrooms, %s m²".formatted(
                        c.street(), c.houseNumber(), c.city(),
                        c.currentPrice() != null ? String.format("%,d", c.currentPrice()).replace(",", ".") : "unknown",
                        c.bedrooms() != null ? c.bedrooms() : "unknown",
                        c.livingAreaM2() != null ? c.livingAreaM2() : "unknown"))
                .collect(Collectors.joining("\n"));

        return """
            Research and rank the following %d property listings, which are already the closest
            matches within %d km of the target location satisfying the requested criteria
            (minimum bedrooms: %s, minimum living area: %s m², price range: %s-%s):

            %s

            Use getListingSummary, getPriceHistory, and compareListings to research these specific
            properties. Do not search for other properties. Write a ranked write-up explaining why
            each one is a good match, considering price, size, bedrooms, and overall value.
            """.formatted(
                candidates.size(), payload.radiusKm(),
                payload.minBedrooms() != null ? payload.minBedrooms() : "any",
                payload.minLivingAreaM2() != null ? payload.minLivingAreaM2() : "any",
                payload.minPrice() != null ? payload.minPrice() : "any",
                payload.maxPrice() != null ? payload.maxPrice() : "any",
                candidateList);
    }
}

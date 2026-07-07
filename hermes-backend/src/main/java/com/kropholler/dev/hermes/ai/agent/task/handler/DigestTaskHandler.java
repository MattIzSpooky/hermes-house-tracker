package com.kropholler.dev.hermes.ai.agent.task.handler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.ai.tool.*;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
class DigestTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavoriteService favoriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public DigestTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                              ListingService listingService,
                              ChatListingCardMapper chatListingCardMapper,
                              ListingSummaryService listingSummaryService,
                              FavoriteService favoriteService,
                              MeterRegistry meterRegistry,
                              ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.favoriteService = favoriteService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.DIGEST; }

    @Override
    public Optional<NotificationContent> handle(AgentTaskEntity task) {
        DigestPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), DigestPayload.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize DigestPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        log.info("Digest task {} started: userId={}, cities={}", task.getId(), task.getUserId(), payload.cities());

        String citiesList = payload.cities().stream().collect(Collectors.joining(", "));
        String prompt = """
            Generate a friendly weekly market digest for these cities: %s.
            For each city:
            1. Call searchListings to find current listings and summarise how many are available and typical price range.
            2. Call findPriceDrop to identify any notable price reductions.
            Write a brief, friendly summary paragraph per city. Keep it concise.
            """.formatted(citiesList);

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID userId = task.getUserId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(userId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        String result = chatClient.prompt()
            .user(prompt)
            .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favTool)
            .call()
            .content();

        if (result == null || result.isBlank()) {
            log.info("Digest task {}: LLM returned no content, skipping notification", task.getId());
            return Optional.empty();
        }
        log.info("Digest task {} completed", task.getId());

        return Optional.of(new NotificationContent(
            "Weekly digest: " + task.getName(), result, List.of()));
    }
}

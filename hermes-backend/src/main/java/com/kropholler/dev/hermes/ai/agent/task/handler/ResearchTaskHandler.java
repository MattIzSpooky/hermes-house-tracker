package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.ResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.ai.tool.*;
import com.kropholler.dev.hermes.favorites.FavoriteService;
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

@Slf4j
@Component
class ResearchTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavoriteService favoriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public ResearchTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
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
    public AgentTaskType getType() { return AgentTaskType.RESEARCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTaskEntity task) {
        ResearchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), ResearchPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ResearchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID userId = task.getUserId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(userId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        String result = chatClient.prompt()
            .user(payload.prompt())
            .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favTool)
            .call()
            .content();

        if (result == null || result.isBlank()) return Optional.empty();

        List<UUID> listingIds = resultHolder.get().stream()
            .map(ChatListingCard::id)
            .toList();

        return Optional.of(new NotificationContent(
            "Research complete: " + task.getName(), result, listingIds));
    }
}

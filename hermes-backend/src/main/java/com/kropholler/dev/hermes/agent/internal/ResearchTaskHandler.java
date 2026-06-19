package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.*;
import com.kropholler.dev.hermes.ai.internal.*;
import com.kropholler.dev.hermes.favourites.FavouriteService;
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
public class ResearchTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavouriteService favouriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public ResearchTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                                ListingService listingService,
                                ChatListingCardMapper chatListingCardMapper,
                                ListingSummaryService listingSummaryService,
                                FavouriteService favouriteService,
                                MeterRegistry meterRegistry,
                                ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.favouriteService = favouriteService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.RESEARCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        ResearchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), ResearchPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ResearchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID clientId = task.getClientId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(clientId, favouriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

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

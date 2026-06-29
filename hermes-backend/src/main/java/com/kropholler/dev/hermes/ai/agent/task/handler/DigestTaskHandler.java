package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.data.AgentTask;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.NotificationContent;
import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.ai.tool.*;
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
import java.util.stream.Collectors;

@Slf4j
@Component
public class DigestTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavouriteService favouriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public DigestTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
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
    public AgentTaskType getType() { return AgentTaskType.DIGEST; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        DigestPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), DigestPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize DigestPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        String citiesList = payload.cities().stream().collect(Collectors.joining(", "));
        String prompt = """
            Generate a friendly weekly market digest for these cities: %s.
            For each city:
            1. Call searchListings to find current listings and summarise how many are available and typical price range.
            2. Call findPriceDrop to identify any notable price reductions.
            Write a brief, friendly summary paragraph per city. Keep it concise.
            """.formatted(citiesList);

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID clientId = task.getClientId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(clientId, favouriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        String result = chatClient.prompt()
            .user(prompt)
            .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favTool)
            .call()
            .content();

        if (result == null || result.isBlank()) return Optional.empty();

        return Optional.of(new NotificationContent(
            "Weekly digest: " + task.getName(), result, List.of()));
    }
}

package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.ChatToolProvider;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.ai.tool.*;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates AI chat for a single session.
 *
 * <p>Session IDs are client-generated UUIDs. There is no server-side authentication:
 * any client that knows a session ID can subscribe to its messages. This is acceptable
 * for a personal/local deployment but must be addressed before any shared deployment.
 */
@Slf4j
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavoriteService favoriteService;
    private final List<ChatToolProvider> chatToolProviders;
    private final MeterRegistry meterRegistry;

    public AiChatService(@Qualifier("chatClient") ChatClient chatClient,
                          ChatMessageRepository chatMessageRepository,
                          ListingService listingService,
                          ChatListingCardMapper chatListingCardMapper,
                          ListingSummaryService listingSummaryService,
                          FavoriteService favoriteService,
                          List<ChatToolProvider> chatToolProviders,
                          MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.chatMessageRepository = chatMessageRepository;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.favoriteService = favoriteService;
        this.chatToolProviders = chatToolProviders;
        this.meterRegistry = meterRegistry;
    }

    public record StreamHandle(Flux<String> tokens, AtomicReference<List<ChatListingCard>> resultHolder) {}

    private static String sanitizeHistory(String content) {
        return content
                .replaceAll("(?i)[^.!?]*funda\\.nl[^.!?]*[.!?]?", "")
                .replaceAll("(?i)[^.!?]*clicking on the [\"']?url[\"']?[^.!?]*[.!?]?", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    @Transactional
    public void saveUserMessage(UUID sessionId, UUID userId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("USER");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    @Transactional
    public void saveAssistantMessage(UUID sessionId, UUID userId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    /**
     * Builds a streaming handle for the given session's next message.
     * History is loaded from the DB before the current user message is saved,
     * so the caller must save both user and assistant messages after streaming completes.
     */
    public StreamHandle startStream(UUID sessionId, UUID userId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        List<ChatMessageEntity> allMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int fromIndex = Math.max(0, allMessages.size() - 20);
        List<Message> history = allMessages.subList(fromIndex, allMessages.size())
                .stream()
                .map(m -> switch (m.getRole()) {
                    case "USER"      -> (Message) new UserMessage(m.getContent());
                    case "ASSISTANT" -> new AssistantMessage(sanitizeHistory(m.getContent()));
                    default -> throw new IllegalStateException("Unknown chat role: " + m.getRole());
                })
                .toList();

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());

        ListingSearchTool searchTool = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetListingSummaryTool summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        GetPriceHistoryTool historyTool = new GetPriceHistoryTool(listingService, meterRegistry);
        CompareListingsTool compareTool = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        FindPriceDropTool priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetFavouriteListingsTool favouritesTool = new GetFavouriteListingsTool(
                userId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        List<Object> allTools = new ArrayList<>(List.of(
                searchTool, summaryTool, historyTool, compareTool, priceDropTool, favouritesTool));
        for (ChatToolProvider provider : chatToolProviders) {
            allTools.addAll(provider.provideTools(userId));
        }

        log.info("startStream: registering {} tools for session={}", allTools.size(), sessionId);

        Flux<String> tokens = chatClient.prompt()
                .messages(history)
                .user(userMessage)
                .tools(allTools.toArray())
                .stream()
                .content();

        return new StreamHandle(tokens, resultHolder);
    }
}

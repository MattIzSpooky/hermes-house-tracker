package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ChatMessage;
import com.kropholler.dev.hermes.ai.internal.ChatMessageRepository;
import com.kropholler.dev.hermes.ai.internal.GetListingSummaryTool;
import com.kropholler.dev.hermes.ai.internal.ListingSearchTool;
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
    private final MeterRegistry meterRegistry;

    public AiChatService(@Qualifier("chatClient") ChatClient chatClient,
                          ChatMessageRepository chatMessageRepository,
                          ListingService listingService,
                          ChatListingCardMapper chatListingCardMapper,
                          ListingSummaryService listingSummaryService,
                          MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.chatMessageRepository = chatMessageRepository;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
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
    public void saveUserMessage(UUID sessionId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("USER");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    @Transactional
    public void saveAssistantMessage(UUID sessionId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    /**
     * Builds a streaming handle for the given session's next message.
     * History is loaded from the DB before the current user message is saved,
     * so the caller must save both user and assistant messages after streaming completes.
     */
    public StreamHandle startStream(UUID sessionId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        List<ChatMessage> allMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        // Cap history at the 20 most recent messages so stale exchanges from earlier
        // sessions don't bias the model (e.g. old empty-result turns for a city).
        int fromIndex = Math.max(0, allMessages.size() - 20);
        List<Message> history = allMessages.subList(fromIndex, allMessages.size())
                .stream()
                .map(m -> switch (m.getRole()) {
                    case "USER"      -> (Message) new UserMessage(m.getContent());
                    // Scrub Funda.nl references from old assistant messages so the model
                    // doesn't keep repeating them based on stale history.
                    case "ASSISTANT" -> new AssistantMessage(sanitizeHistory(m.getContent()));
                    default -> throw new IllegalStateException("Unknown chat role: " + m.getRole());
                })
                .toList();

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        ListingSearchTool searchTool = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetListingSummaryTool summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);

        Flux<String> tokens = chatClient.prompt()
                .messages(history)
                .user(userMessage)
                .tools(searchTool, summaryTool)
                .stream()
                .content();

        return new StreamHandle(tokens, resultHolder);
    }
}

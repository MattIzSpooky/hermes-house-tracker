package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ChatMessage;
import com.kropholler.dev.hermes.ai.internal.ChatMessageRepository;
import com.kropholler.dev.hermes.ai.internal.ListingSearchTool;
import com.kropholler.dev.hermes.listing.ListingService;
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

@Slf4j
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;

    public AiChatService(@Qualifier("chatClient") ChatClient chatClient,
                          ChatMessageRepository chatMessageRepository,
                          ListingService listingService,
                          ChatListingCardMapper chatListingCardMapper) {
        this.chatClient = chatClient;
        this.chatMessageRepository = chatMessageRepository;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
    }

    public record StreamHandle(Flux<String> tokens, AtomicReference<List<ChatListingCard>> resultHolder) {}

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
     * Must be called AFTER {@link #saveUserMessage} has persisted the current user turn —
     * {@code startStream} loads history from the DB, and the current message must already
     * be present to appear in the conversation context sent to the LLM.
     */
    public StreamHandle startStream(UUID sessionId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        List<Message> history = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(m -> switch (m.getRole()) {
                    case "USER"      -> (Message) new UserMessage(m.getContent());
                    case "ASSISTANT" -> new AssistantMessage(m.getContent());
                    default -> throw new IllegalStateException("Unknown chat role: " + m.getRole());
                })
                .toList();

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder);

        Flux<String> tokens = chatClient.prompt()
                .messages(history)
                .user(userMessage)
                .tools(tool)
                .stream()
                .content();

        return new StreamHandle(tokens, resultHolder);
    }
}

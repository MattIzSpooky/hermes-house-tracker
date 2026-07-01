package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.AiChatService;
import com.kropholler.dev.hermes.ai.chat.ChatController;
import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatMessageRequest;
import com.kropholler.dev.hermes.ai.chat.ResultFrame;
import com.kropholler.dev.hermes.ai.chat.TokenFrame;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock AiChatService aiChatService;
    @Mock SimpMessagingTemplate messaging;
    ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(aiChatService, messaging, new SimpleMeterRegistry());
    }

    @Test
    void handleMessage_streamsTokensAndSendsEmptyResult() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses in Utrecht", null);
        AiChatService.StreamHandle handle = handle(Flux.just("I ", "found ", "nothing."), List.of());
        when(aiChatService.startStream(sessionId, null, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        verify(aiChatService).startStream(sessionId, null, request.message());
        verify(messaging, times(3)).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("TOKEN")));
        verify(aiChatService).saveUserMessage(sessionId, request.message());
        verify(aiChatService).saveAssistantMessage(sessionId, "I found nothing.");
        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf && rf.listings().isEmpty()));
    }

    @Test
    void handleMessage_withListings_sendsResultFrameWithCards() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "3 bedrooms Amsterdam", null);
        ChatListingCard card = new ChatListingCard(UUID.randomUUID(), "Keizersgracht", "1",
                null, "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE");
        AiChatService.StreamHandle handle = handle(Flux.just("Here you go."), List.of(card));
        when(aiChatService.startStream(sessionId, null, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf
                        && rf.listings().size() == 1
                        && rf.listings().get(0).city().equals("Amsterdam")));
    }

    @Test
    void handleMessage_whitespaceOnlyResponse_doesNotSaveAssistantMessage() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "hello", null);
        AiChatService.StreamHandle handle = handle(Flux.just("   "), List.of());
        when(aiChatService.startStream(sessionId, null, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        verify(aiChatService).saveUserMessage(sessionId, request.message());
        verify(aiChatService, never()).saveAssistantMessage(any(), any());
    }

    @Test
    void handleMessage_serviceThrows_sendsErrorTokenFrame() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses", null);
        AiChatService.StreamHandle handle = handle(Flux.error(new RuntimeException("LLM timeout")), List.of());
        when(aiChatService.startStream(sessionId, null, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("ERROR")));
        verify(aiChatService, never()).saveUserMessage(any(), any());
        verify(aiChatService, never()).saveAssistantMessage(any(), any());
        verify(messaging, never()).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame));
    }

    @Test
    void handleMessage_nullRequest_returnsEarlyWithoutStreaming() {
        controller.handleMessage(null);

        verify(aiChatService, never()).startStream(any(), any(), any());
        verify(messaging, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void handleMessage_nullSessionId_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(null, "hello", null));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    @Test
    void handleMessage_nullMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), null, null));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    @Test
    void handleMessage_blankMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), "   ", null));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    private AiChatService.StreamHandle handle(Flux<String> tokens, List<ChatListingCard> cards) {
        return new AiChatService.StreamHandle(tokens, new AtomicReference<>(cards));
    }
}

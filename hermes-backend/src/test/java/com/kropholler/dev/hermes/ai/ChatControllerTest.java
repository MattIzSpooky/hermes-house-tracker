package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ResultFrame;
import com.kropholler.dev.hermes.ai.internal.TokenFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock AiChatService aiChatService;
    @Mock SimpMessagingTemplate messaging;
    @InjectMocks ChatController controller;

    @Test
    void handleMessage_streamsTokensAndSendsEmptyResult() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses in Utrecht");

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AiChatService.StreamHandle handle = new AiChatService.StreamHandle(
                Flux.just("I ", "found ", "nothing."), holder);

        when(aiChatService.startStream(sessionId, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        // Verify ordering: startStream first, then saves after
        verify(aiChatService).startStream(sessionId, request.message());
        // Token frames sent during streaming
        verify(messaging, times(3)).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("TOKEN")));
        // Saves happen AFTER streaming
        verify(aiChatService).saveUserMessage(sessionId, request.message());
        verify(aiChatService).saveAssistantMessage(sessionId, "I found nothing.");
        // ResultFrame sent last
        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf && rf.listings().isEmpty()));
    }

    @Test
    void handleMessage_withListings_sendsResultFrameWithCards() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "3 bedrooms Amsterdam");

        ChatListingCard card = new ChatListingCard(UUID.randomUUID(), "Keizersgracht", "1",
                null, "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE",
                "https://funda.nl/listing/1");
        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of(card));
        AiChatService.StreamHandle handle = new AiChatService.StreamHandle(Flux.just("Here you go."), holder);

        when(aiChatService.startStream(sessionId, request.message())).thenReturn(handle);

        controller.handleMessage(request);

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf && rf.listings().size() == 1
                        && rf.listings().get(0).city().equals("Amsterdam")));
    }

    @Test
    void handleMessage_serviceThrows_sendsErrorTokenFrame() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses");

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AiChatService.StreamHandle handle = new AiChatService.StreamHandle(
                Flux.error(new RuntimeException("LLM timeout")), holder);

        when(aiChatService.startStream(sessionId, request.message())).thenReturn(handle);

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
}

package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.profile.UserProfileService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock AiChatService aiChatService;
    @Mock UserProfileService userProfileService;
    @Mock SimpMessagingTemplate messaging;
    ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(aiChatService, userProfileService, messaging, new SimpleMeterRegistry());
    }

    private Principal principalFor(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("email", "user@hermes.local")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void handleMessage_streamsTokensAndSendsEmptyResult() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses in Utrecht");
        AiChatService.StreamHandle handle = handle(Flux.just("I ", "found ", "nothing."), List.of());
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(aiChatService).startStream(sessionId, userId, "user@hermes.local", request.message());
        verify(messaging, times(3)).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("TOKEN")));
        verify(aiChatService).saveUserMessage(sessionId, userId, request.message());
        verify(aiChatService).saveAssistantMessage(sessionId, userId, "I found nothing.");
        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf && rf.listings().isEmpty()));
    }

    @Test
    void handleMessage_withListings_sendsResultFrameWithCards() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "3 bedrooms Amsterdam");
        ChatListingCard card = new ChatListingCard(UUID.randomUUID(), "Keizersgracht", "1",
                null, "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE");
        AiChatService.StreamHandle handle = handle(Flux.just("Here you go."), List.of(card));
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf
                        && rf.listings().size() == 1
                        && rf.listings().get(0).city().equals("Amsterdam")));
    }

    @Test
    void handleMessage_whitespaceOnlyResponse_doesNotSaveAssistantMessage() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "hello");
        AiChatService.StreamHandle handle = handle(Flux.just("   "), List.of());
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(aiChatService).saveUserMessage(sessionId, userId, request.message());
        verify(aiChatService, never()).saveAssistantMessage(any(), any(), any());
    }

    @Test
    void handleMessage_serviceThrows_sendsErrorTokenFrame() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses");
        AiChatService.StreamHandle handle = handle(Flux.error(new RuntimeException("LLM timeout")), List.of());
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("ERROR")));
        verify(aiChatService, never()).saveUserMessage(any(), any(), any());
        verify(aiChatService, never()).saveAssistantMessage(any(), any(), any());
        verify(messaging, never()).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame));
    }

    @Test
    void handleMessage_nullRequest_returnsEarlyWithoutStreaming() {
        controller.handleMessage(null, principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any(), any());
        verify(messaging, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void handleMessage_nullSessionId_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(null, "hello"), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any(), any());
    }

    @Test
    void handleMessage_nullMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), null), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any(), any());
    }

    @Test
    void handleMessage_blankMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), "   "), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any(), any());
    }

    @Test
    void handleMessage_syncsEmailFromJwt() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "hello");
        AiChatService.StreamHandle handle = handle(Flux.just("hi"), List.of());
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(userProfileService).syncEmail(userId, "user@hermes.local");
    }

    private AiChatService.StreamHandle handle(Flux<String> tokens, List<ChatListingCard> cards) {
        return new AiChatService.StreamHandle(tokens, new AtomicReference<>(cards));
    }
}

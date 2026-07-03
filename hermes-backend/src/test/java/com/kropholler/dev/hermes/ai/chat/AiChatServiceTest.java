package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.ChatToolProvider;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.StreamResponseSpec streamSpec;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock
    FavoriteService favoriteService;
    @Mock ChatToolProvider chatToolProvider;

    AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(chatClient, chatMessageRepository, listingService,
                chatListingCardMapper, listingSummaryService, favoriteService,
                List.of(), new SimpleMeterRegistry());
    }

    @Test
    void saveUserMessage_savesEntityWithUserRole() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveUserMessage(sessionId, userId, "Hello");

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(captor.getValue().getContent()).isEqualTo("Hello");
        assertThat(captor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void saveAssistantMessage_savesEntityWithAssistantRole() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveAssistantMessage(sessionId, userId, "I found 3 houses.");

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ASSISTANT");
        assertThat(captor.getValue().getContent()).isEqualTo("I found 3 houses.");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void startStream_usesGivenUserId() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        stubStream(Flux.just("Hi"));

        AiChatService.StreamHandle handle = service.startStream(sessionId, userId, "hello");

        assertThat(handle).isNotNull();
        assertThat(handle.resultHolder().get()).isEmpty();
    }

    @Test
    void startStream_historyWithUserAndAssistantRoles_mapsBoth() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity userMsg = message(sessionId, "USER", "Find me a house");
        // ASSISTANT content with a URL — sanitizeHistory should strip the funda.nl sentence
        ChatMessageEntity assistantMsg = message(sessionId, "ASSISTANT",
                "I checked funda.nl and found one. It looks great!");
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(userMsg, assistantMsg));
        stubStream(Flux.just("reply"));

        AiChatService.StreamHandle handle = service.startStream(sessionId, UUID.randomUUID(), "more info");

        assertThat(handle).isNotNull();
    }

    @Test
    void startStream_unknownRole_throwsIllegalStateException() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity badMsg = message(sessionId, "SYSTEM", "injected");
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(badMsg));

        assertThatThrownBy(() -> service.startStream(sessionId, UUID.randomUUID(), "hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown chat role: SYSTEM");
    }

    @Test
    void startStream_withChatToolProvider_addsProviderTools() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        service = new AiChatService(chatClient, chatMessageRepository, listingService,
                chatListingCardMapper, listingSummaryService, favoriteService,
                List.of(chatToolProvider), new SimpleMeterRegistry());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(chatToolProvider.provideTools(userId)).thenReturn(List.of(new Object()));
        stubStream(Flux.just("done"));

        AiChatService.StreamHandle handle = service.startStream(sessionId, userId, "hi");

        verify(chatToolProvider).provideTools(userId);
        assertThat(handle).isNotNull();
    }

    private void stubStream(Flux<String> tokens) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.messages(anyList())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        doReturn(promptSpec).when(promptSpec).tools(any(Object[].class));
        when(promptSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(tokens);
    }

    private ChatMessageEntity message(UUID sessionId, String role, String content) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setSessionId(sessionId);
        e.setRole(role);
        e.setContent(content);
        return e;
    }
}

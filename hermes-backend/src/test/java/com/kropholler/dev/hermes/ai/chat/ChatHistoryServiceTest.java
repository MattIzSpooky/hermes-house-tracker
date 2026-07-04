package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock ChatMessageRepository chatMessageRepository;
    @InjectMocks ChatHistoryService service;

    private ChatSessionProjection projection(UUID sessionId, String titleSource, Instant lastMessageAt) {
        return new ChatSessionProjection() {
            @Override public UUID getSessionId() { return sessionId; }
            @Override public Instant getLastMessageAt() { return lastMessageAt; }
            @Override public String getTitleSource() { return titleSource; }
        };
    }

    @Test
    void listSessions_mapsProjectionsToDtosWithTruncatedTitle() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        String longMessage = "x".repeat(80);
        when(chatMessageRepository.findSessionSummariesByUserId(userId))
            .thenReturn(List.of(projection(sessionId, longMessage, lastMessageAt)));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sessionId()).isEqualTo(sessionId);
        assertThat(result.get(0).lastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(result.get(0).title()).hasSize(61); // 60 chars + ellipsis
        assertThat(result.get(0).title()).endsWith("…");
    }

    @Test
    void listSessions_shortTitleSourceIsNotTruncated() {
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.findSessionSummariesByUserId(userId))
            .thenReturn(List.of(projection(UUID.randomUUID(), "Short message", Instant.now())));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEqualTo("Short message");
    }

    @Test
    void getMessages_mapsEntitiesToDtos() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setRole("USER");
        entity.setContent("Hello");
        when(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId))
            .thenReturn(List.of(entity));

        List<ChatMessageDto> result = service.getMessages(userId, sessionId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(0).content()).isEqualTo("Hello");
    }

    @Test
    void deleteSession_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        service.deleteSession(userId, sessionId);

        verify(chatMessageRepository).deleteBySessionIdAndUserId(sessionId, userId);
    }
}

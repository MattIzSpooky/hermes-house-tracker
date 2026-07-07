package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock ChatMessageRepository chatMessageRepository;
    @InjectMocks ChatHistoryService service;

    private ChatSessionOverviewProjection overview(UUID sessionId, Instant lastMessageAt) {
        return new ChatSessionOverviewProjection() {
            @Override public UUID getSessionId() { return sessionId; }
            @Override public Instant getLastMessageAt() { return lastMessageAt; }
        };
    }

    private ChatMessageEntity userMessage(UUID sessionId, UUID userId, String content) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setSessionId(sessionId);
        e.setUserId(userId);
        e.setRole("USER");
        e.setContent(content);
        return e;
    }

    @Test
    void listSessions_mapsOverviewsToDtosWithTruncatedTitle() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        String longMessage = "x".repeat(80);
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, lastMessageAt)));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.of(userMessage(sessionId, userId, longMessage)));

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
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, Instant.now())));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.of(userMessage(sessionId, userId, "Short message")));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEqualTo("Short message");
    }

    @Test
    void listSessions_noUserMessageYet_titleIsEmptyString() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, Instant.now())));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.empty());

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEmpty();
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

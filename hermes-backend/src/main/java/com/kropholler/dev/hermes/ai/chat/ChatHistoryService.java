package com.kropholler.dev.hermes.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final int MAX_TITLE_LENGTH = 60;

    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSessionSummaryDto> listSessions(UUID userId) {
        return chatMessageRepository.findSessionSummariesByUserId(userId).stream()
            .map(p -> new ChatSessionSummaryDto(p.getSessionId(), truncateTitle(p.getTitleSource()), p.getLastMessageAt()))
            .toList();
    }

    public List<ChatMessageDto> getMessages(UUID userId, UUID sessionId) {
        return chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId).stream()
            .map(e -> new ChatMessageDto(e.getRole(), e.getContent(), e.getCreatedAt()))
            .toList();
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);
    }

    private static String truncateTitle(String titleSource) {
        if (titleSource == null) {
            return "";
        }
        String trimmed = titleSource.strip();
        return trimmed.length() <= MAX_TITLE_LENGTH ? trimmed : trimmed.substring(0, MAX_TITLE_LENGTH) + "…";
    }
}

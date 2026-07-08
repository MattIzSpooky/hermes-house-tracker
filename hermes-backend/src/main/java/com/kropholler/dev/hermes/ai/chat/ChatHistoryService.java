package com.kropholler.dev.hermes.ai.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_SESSIONS = 50;

    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSessionSummaryDto> listSessions(UUID userId) {
        List<ChatSessionSummaryDto> sessions = chatMessageRepository.findSessionOverviewsByUserId(userId).stream()
            .limit(MAX_SESSIONS)
            .map(o -> new ChatSessionSummaryDto(
                o.getSessionId(),
                truncateTitle(titleSourceFor(userId, o.getSessionId())),
                o.getLastMessageAt()))
            .toList();
        log.debug("listSessions returned {} session(s) for user {}", sessions.size(), userId);
        return sessions;
    }

    public List<ChatMessageDto> getMessages(UUID userId, UUID sessionId) {
        List<ChatMessageDto> messages = chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId).stream()
            .map(e -> new ChatMessageDto(e.getRole(), e.getContent(), e.getCreatedAt()))
            .toList();
        log.debug("getMessages returned {} message(s) for user={}, session={}", messages.size(), userId, sessionId);
        return messages;
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);
        log.info("Deleted chat session {} for user {}", sessionId, userId);
    }

    private String titleSourceFor(UUID userId, UUID sessionId) {
        return chatMessageRepository
            .findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER")
            .map(ChatMessageEntity::getContent)
            .orElse(null);
    }

    private static String truncateTitle(String titleSource) {
        if (titleSource == null) {
            return "";
        }
        String trimmed = titleSource.strip();
        return trimmed.length() <= MAX_TITLE_LENGTH ? trimmed : trimmed.substring(0, MAX_TITLE_LENGTH) + "…";
    }
}

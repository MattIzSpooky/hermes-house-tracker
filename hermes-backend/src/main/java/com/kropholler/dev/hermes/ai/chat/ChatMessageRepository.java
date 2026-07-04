package com.kropholler.dev.hermes.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID sessionId, UUID userId);

    @Query(value = """
            SELECT
              m.session_id AS sessionId,
              MAX(m.created_at) AS lastMessageAt,
              (SELECT m2.content FROM chat_messages m2
               WHERE m2.session_id = m.session_id AND m2.user_id = m.user_id AND m2.role = 'USER'
               ORDER BY m2.created_at ASC LIMIT 1) AS titleSource
            FROM chat_messages m
            WHERE m.user_id = :userId
            GROUP BY m.session_id, m.user_id
            ORDER BY MAX(m.created_at) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<ChatSessionProjection> findSessionSummariesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM chat_messages WHERE session_id = :sessionId AND user_id = :userId", nativeQuery = true)
    void deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);
}

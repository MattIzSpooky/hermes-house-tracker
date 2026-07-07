package com.kropholler.dev.hermes.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID sessionId, UUID userId);

    @Query("""
            SELECT m.sessionId AS sessionId, MAX(m.createdAt) AS lastMessageAt
            FROM ChatMessageEntity m
            WHERE m.userId = :userId
            GROUP BY m.sessionId
            ORDER BY MAX(m.createdAt) DESC
            """)
    List<ChatSessionOverviewProjection> findSessionOverviewsByUserId(@Param("userId") UUID userId);

    Optional<ChatMessageEntity> findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(
        UUID sessionId, UUID userId, String role);

    @Modifying
    @Query(value = "DELETE FROM chat_messages WHERE session_id = :sessionId AND user_id = :userId", nativeQuery = true)
    void deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);

    List<ChatMessageEntity> findByEncryptionKeyVersionLessThan(int version, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessageEntity m SET m.content = :content, m.encryptionKeyVersion = :version WHERE m.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("content") String content, @Param("version") int version);
}

package com.kropholler.dev.hermes.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID sessionId, UUID userId);
}

package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public interface ChatSessionProjection {
    UUID getSessionId();
    Instant getLastMessageAt();
    String getTitleSource();
}

package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public interface ChatSessionOverviewProjection {
    UUID getSessionId();
    Instant getLastMessageAt();
}

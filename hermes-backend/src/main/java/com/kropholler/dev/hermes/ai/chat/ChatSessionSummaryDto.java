package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionSummaryDto(UUID sessionId, String title, Instant lastMessageAt) {}

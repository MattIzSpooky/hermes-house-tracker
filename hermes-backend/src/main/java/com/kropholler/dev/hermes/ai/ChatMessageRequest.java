package com.kropholler.dev.hermes.ai;

import java.util.UUID;

public record ChatMessageRequest(UUID sessionId, String message) {}

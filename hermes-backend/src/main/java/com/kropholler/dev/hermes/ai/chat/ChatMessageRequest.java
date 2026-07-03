package com.kropholler.dev.hermes.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChatMessageRequest(
        @NotNull UUID sessionId,
        @NotBlank String message
) {}

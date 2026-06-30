package com.kropholler.dev.hermes.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    UUID taskId,
    UUID clientId,
    String title,
    String body,
    List<UUID> listingIds,
    boolean read,
    Instant createdAt,
    Instant emailSentAt
) {}

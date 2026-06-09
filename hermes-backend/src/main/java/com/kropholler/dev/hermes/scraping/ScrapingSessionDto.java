package com.kropholler.dev.hermes.scraping;

import java.time.Instant;
import java.util.UUID;

public record ScrapingSessionDto(
    UUID id,
    ScrapingSessionStatus status,
    ScrapingSessionType type,
    Instant createdAt,
    Instant completedAt
) {}

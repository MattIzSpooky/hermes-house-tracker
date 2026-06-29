package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;

import java.time.Instant;
import java.util.UUID;

public record ScrapingSessionDto(
    UUID id,
    ScrapingSessionStatus status,
    ScrapingSessionType type,
    Instant createdAt,
    Instant completedAt
) {}

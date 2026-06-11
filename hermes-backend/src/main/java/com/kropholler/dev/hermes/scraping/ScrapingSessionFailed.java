package com.kropholler.dev.hermes.scraping;

import java.util.UUID;

public record ScrapingSessionFailed(UUID sessionId, String reason, String correlationId) {}

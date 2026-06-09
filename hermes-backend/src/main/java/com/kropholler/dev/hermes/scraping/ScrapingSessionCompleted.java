package com.kropholler.dev.hermes.scraping;

import java.util.List;
import java.util.UUID;

public record ScrapingSessionCompleted(UUID sessionId, List<RawListing> listings) {}

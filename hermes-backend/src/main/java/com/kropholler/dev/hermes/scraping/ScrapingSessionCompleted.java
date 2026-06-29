package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.funda.RawListing;

import java.util.List;
import java.util.UUID;

public record ScrapingSessionCompleted(UUID sessionId, ScrapingSessionType type, List<RawListing> listings) {}

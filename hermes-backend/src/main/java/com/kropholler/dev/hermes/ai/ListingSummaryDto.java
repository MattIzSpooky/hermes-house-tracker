package com.kropholler.dev.hermes.ai;

import java.time.Instant;
import java.util.UUID;

public record ListingSummaryDto(UUID listingId, String summary, Instant generatedAt) {}

package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingStatus;
import java.time.Instant;

public record StatusPoint(Instant scrapedAt, ListingStatus status) {}

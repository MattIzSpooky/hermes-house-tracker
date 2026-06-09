package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingStatus;

import java.util.List;
import java.util.UUID;

public record ListingReport(
    UUID listingId,
    Long daysListedOnFunda,
    Long daysInHermes,
    Integer currentPrice,
    Integer initialPrice,
    Double priceChangePct,
    List<PricePoint> priceHistory,
    List<StatusPoint> statusHistory,
    ListingStatus currentStatus
) {}

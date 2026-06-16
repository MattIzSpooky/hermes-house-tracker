package com.kropholler.dev.hermes.listing;

public record PriceDropResult(
        ListingDto listing,
        int originalPrice,
        int currentPrice,
        double dropPercent
) {}

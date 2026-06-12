package com.kropholler.dev.hermes.listing;

import java.util.List;
import java.util.UUID;

public record PriceHistoryUpdated(List<UUID> listingIds) {}

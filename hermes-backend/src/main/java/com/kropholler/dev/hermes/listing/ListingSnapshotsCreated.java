package com.kropholler.dev.hermes.listing;

import java.util.List;
import java.util.UUID;

public record ListingSnapshotsCreated(List<UUID> listingIds) {}

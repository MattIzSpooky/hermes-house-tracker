package com.kropholler.dev.hermes.listing.pricehistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceHistoryEntryRepository extends JpaRepository<PriceHistoryEntry, UUID> {

    List<PriceHistoryEntry> findByListingIdOrderByTimestampAsc(UUID listingId);

    Optional<PriceHistoryEntry> findFirstByListingIdAndStatusOrderByTimestampDesc(
            UUID listingId, String status);

    boolean existsByListingIdAndTimestamp(UUID listingId, Instant timestamp);
}

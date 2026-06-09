package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingSnapshotRepository extends JpaRepository<ListingSnapshot, UUID> {
    List<ListingSnapshot> findByListingIdOrderByScrapedAtAsc(UUID listingId);
    Optional<ListingSnapshot> findTopByListingIdOrderByScrapedAtDesc(UUID listingId);
    Page<ListingSnapshot> findByListingId(UUID listingId, Pageable pageable);
}

package com.kropholler.dev.hermes.listing.summary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingSummaryRepository extends JpaRepository<ListingSummaryEntity, UUID> {
    Optional<ListingSummaryEntity> findByListingId(UUID listingId);
}

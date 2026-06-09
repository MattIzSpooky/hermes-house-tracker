package com.kropholler.dev.hermes.ai.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingSummaryRepository extends JpaRepository<ListingSummary, UUID> {
    Optional<ListingSummary> findByListingId(UUID listingId);
}

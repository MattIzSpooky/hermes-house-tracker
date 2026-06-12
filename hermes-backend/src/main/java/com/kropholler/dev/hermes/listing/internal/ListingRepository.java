package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    Optional<Listing> findByFundaId(String fundaId);
    Page<Listing> findAllByDeletedAtIsNull(Pageable pageable);
    void deleteAllByDeletedAtIsNotNull();
}

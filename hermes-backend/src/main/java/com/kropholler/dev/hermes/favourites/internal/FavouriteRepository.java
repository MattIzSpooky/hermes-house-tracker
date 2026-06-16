package com.kropholler.dev.hermes.favourites.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavouriteRepository extends JpaRepository<Favourite, UUID> {
    List<Favourite> findByClientId(UUID clientId);
    Optional<Favourite> findByClientIdAndListingId(UUID clientId, UUID listingId);
    void deleteByClientIdAndListingId(UUID clientId, UUID listingId);
    boolean existsByClientIdAndListingId(UUID clientId, UUID listingId);
}

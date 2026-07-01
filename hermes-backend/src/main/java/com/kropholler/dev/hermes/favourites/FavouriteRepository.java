package com.kropholler.dev.hermes.favourites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavouriteRepository extends JpaRepository<FavouriteEntity, UUID> {
    List<FavouriteEntity> findByClientId(UUID clientId);
    Optional<FavouriteEntity> findByClientIdAndListingId(UUID clientId, UUID listingId);
    void deleteByClientIdAndListingId(UUID clientId, UUID listingId);
    boolean existsByClientIdAndListingId(UUID clientId, UUID listingId);
}

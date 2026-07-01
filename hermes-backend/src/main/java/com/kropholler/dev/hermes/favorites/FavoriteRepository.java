package com.kropholler.dev.hermes.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, UUID> {
    List<FavoriteEntity> findByClientId(UUID clientId);
    Optional<FavoriteEntity> findByClientIdAndListingId(UUID clientId, UUID listingId);
    void deleteByClientIdAndListingId(UUID clientId, UUID listingId);
    boolean existsByClientIdAndListingId(UUID clientId, UUID listingId);
}

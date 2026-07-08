package com.kropholler.dev.hermes.favorites;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository repository;

    @Transactional(readOnly = true)
    public List<FavoriteDto> findByUserId(UUID userId) {
        List<FavoriteDto> favorites = repository.findByUserId(userId).stream()
                .map(f -> new FavoriteDto(f.getListingId(), f.getSavedAt()))
                .toList();
        log.debug("findByUserId returned {} favorite(s) for user {}", favorites.size(), userId);
        return favorites;
    }

    @Transactional
    public void addFavorite(UUID userId, UUID listingId) {
        if (!repository.existsByUserIdAndListingId(userId, listingId)) {
            FavoriteEntity f = new FavoriteEntity();
            f.setUserId(userId);
            f.setListingId(listingId);
            repository.save(f);
            log.info("Added favorite: user={}, listing={}", userId, listingId);
        } else {
            log.debug("addFavorite skipped, already favorited: user={}, listing={}", userId, listingId);
        }
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID listingId) {
        repository.deleteByUserIdAndListingId(userId, listingId);
        log.info("Removed favorite: user={}, listing={}", userId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID listingId) {
        return repository.existsByUserIdAndListingId(userId, listingId);
    }
}

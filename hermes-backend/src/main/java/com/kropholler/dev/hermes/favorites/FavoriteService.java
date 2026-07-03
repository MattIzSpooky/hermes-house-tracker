package com.kropholler.dev.hermes.favorites;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository repository;

    @Transactional(readOnly = true)
    public List<FavoriteDto> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream()
                .map(f -> new FavoriteDto(f.getListingId(), f.getSavedAt()))
                .toList();
    }

    @Transactional
    public void addFavorite(UUID userId, UUID listingId) {
        if (!repository.existsByUserIdAndListingId(userId, listingId)) {
            FavoriteEntity f = new FavoriteEntity();
            f.setUserId(userId);
            f.setListingId(listingId);
            repository.save(f);
        }
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID listingId) {
        repository.deleteByUserIdAndListingId(userId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID listingId) {
        return repository.existsByUserIdAndListingId(userId, listingId);
    }
}

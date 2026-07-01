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
    public List<FavoriteDto> findByClientId(UUID clientId) {
        return repository.findByClientId(clientId).stream()
                .map(f -> new FavoriteDto(f.getListingId(), f.getSavedAt()))
                .toList();
    }

    @Transactional
    public void addFavorite(UUID clientId, UUID listingId) {
        if (!repository.existsByClientIdAndListingId(clientId, listingId)) {
            FavoriteEntity f = new FavoriteEntity();
            f.setClientId(clientId);
            f.setListingId(listingId);
            repository.save(f);
        }
    }

    @Transactional
    public void removeFavorite(UUID clientId, UUID listingId) {
        repository.deleteByClientIdAndListingId(clientId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID clientId, UUID listingId) {
        return repository.existsByClientIdAndListingId(clientId, listingId);
    }
}

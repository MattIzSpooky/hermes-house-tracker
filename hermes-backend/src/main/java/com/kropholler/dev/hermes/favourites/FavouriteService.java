package com.kropholler.dev.hermes.favourites;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavouriteService {

    private final FavouriteRepository repository;

    @Transactional(readOnly = true)
    public List<FavouriteDto> findByClientId(UUID clientId) {
        return repository.findByClientId(clientId).stream()
                .map(f -> new FavouriteDto(f.getListingId(), f.getSavedAt()))
                .toList();
    }

    @Transactional
    public void addFavourite(UUID clientId, UUID listingId) {
        if (!repository.existsByClientIdAndListingId(clientId, listingId)) {
            Favourite f = new Favourite();
            f.setClientId(clientId);
            f.setListingId(listingId);
            repository.save(f);
        }
    }

    @Transactional
    public void removeFavourite(UUID clientId, UUID listingId) {
        repository.deleteByClientIdAndListingId(clientId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavourite(UUID clientId, UUID listingId) {
        return repository.existsByClientIdAndListingId(clientId, listingId);
    }
}

package com.kropholler.dev.hermes.favorites;


import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.favorites.openapi.FavoritesApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FavoriteController implements FavoritesApi {

    private final FavoriteService favoriteService;
    private final FavoriteApiMapper favoriteApiMapper;

    @Override
    public ResponseEntity<List<FavoriteResponse>> getFavorites(UUID clientId) {
        List<FavoriteResponse> responses = favoriteService.findByClientId(clientId)
            .stream().map(favoriteApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> addFavorite(UUID clientId, UUID listingId) {
        favoriteService.addFavorite(clientId, listingId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavorite(UUID clientId, UUID listingId) {
        favoriteService.removeFavorite(clientId, listingId);
        return ResponseEntity.noContent().build();
    }
}

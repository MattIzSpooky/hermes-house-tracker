package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.favorites.openapi.FavoritesApi;
import com.kropholler.dev.hermes.security.CurrentUser;
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
    public ResponseEntity<List<FavoriteResponse>> getFavorites() {
        UUID userId = CurrentUser.current().id();
        List<FavoriteResponse> responses = favoriteService.findByUserId(userId)
            .stream().map(favoriteApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> addFavorite(UUID listingId) {
        favoriteService.addFavorite(CurrentUser.current().id(), listingId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavorite(UUID listingId) {
        favoriteService.removeFavorite(CurrentUser.current().id(), listingId);
        return ResponseEntity.noContent().build();
    }
}

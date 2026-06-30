package com.kropholler.dev.hermes.favourites;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FavouriteController implements FavouritesApi {

    private final FavouriteService favouriteService;
    private final FavouriteApiMapper favouriteApiMapper;

    @Override
    public ResponseEntity<List<FavouriteResponse>> getFavourites(UUID clientId) {
        List<FavouriteResponse> responses = favouriteService.findByClientId(clientId)
            .stream().map(favouriteApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> addFavourite(UUID clientId, UUID listingId) {
        favouriteService.addFavourite(clientId, listingId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavourite(UUID clientId, UUID listingId) {
        favouriteService.removeFavourite(clientId, listingId);
        return ResponseEntity.noContent().build();
    }
}

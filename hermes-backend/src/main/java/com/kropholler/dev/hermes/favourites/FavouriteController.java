package com.kropholler.dev.hermes.favourites;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/favourites")
@RequiredArgsConstructor
public class FavouriteController {

    private final FavouriteService favouriteService;

    @GetMapping("/{clientId}")
    public List<FavouriteDto> getFavourites(@PathVariable UUID clientId) {
        return favouriteService.findByClientId(clientId);
    }

    @PutMapping("/{clientId}/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavourite(@PathVariable UUID clientId, @PathVariable UUID listingId) {
        favouriteService.addFavourite(clientId, listingId);
    }

    @DeleteMapping("/{clientId}/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavourite(@PathVariable UUID clientId, @PathVariable UUID listingId) {
        favouriteService.removeFavourite(clientId, listingId);
    }
}

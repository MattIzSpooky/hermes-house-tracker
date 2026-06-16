package com.kropholler.dev.hermes.favourites;

import java.time.Instant;
import java.util.UUID;

public record FavouriteDto(UUID listingId, Instant savedAt) {}

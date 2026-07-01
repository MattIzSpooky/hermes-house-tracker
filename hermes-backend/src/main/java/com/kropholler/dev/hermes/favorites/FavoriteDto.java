package com.kropholler.dev.hermes.favorites;

import java.time.Instant;
import java.util.UUID;

public record FavoriteDto(UUID listingId, Instant savedAt) {}

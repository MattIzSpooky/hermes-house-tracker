package com.kropholler.dev.hermes.listing.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record FetchGeocodingCommand(UUID listingId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}

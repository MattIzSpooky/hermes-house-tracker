package com.kropholler.dev.hermes.listing.async.command;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record FetchPriceHistoryCommand(UUID listingId, String fundaId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}

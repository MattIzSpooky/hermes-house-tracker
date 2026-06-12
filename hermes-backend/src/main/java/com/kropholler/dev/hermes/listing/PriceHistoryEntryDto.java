package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceHistoryEntryDto(
    UUID id,
    Integer price,
    String status,
    String source,
    LocalDate date,
    Instant timestamp
) {}

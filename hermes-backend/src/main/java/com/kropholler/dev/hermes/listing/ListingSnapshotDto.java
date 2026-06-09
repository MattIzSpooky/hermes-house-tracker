package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ListingSnapshotDto(
    UUID id,
    Instant scrapedAt,
    Integer askingPrice,
    Integer livingAreaM2,
    Integer rooms,
    String energyLabel,
    LocalDate listedOnFundaSince,
    ListingStatus status
) {}

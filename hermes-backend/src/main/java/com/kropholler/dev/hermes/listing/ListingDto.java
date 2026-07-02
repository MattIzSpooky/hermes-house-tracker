package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.util.UUID;

public record ListingDto(
    UUID id,
    String fundaId,
    String url,
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Integer currentPrice,
    ListingStatus status,
    String description,
    Integer livingAreaM2,
    Integer rooms,
    Integer bedrooms,
    String energyLabel,
    Integer plotAreaM2,
    GeoLocation location
) {}

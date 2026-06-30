package com.kropholler.dev.hermes.funda;

public record RawListing(
    String fundaId,
    String url,
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Integer askingPrice,
    String status,
    String description,
    Integer livingAreaM2,
    Integer rooms,
    Integer bedrooms,
    String energyLabel,
    Integer plotAreaM2
) {}

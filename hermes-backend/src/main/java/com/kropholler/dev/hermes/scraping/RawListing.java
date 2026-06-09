package com.kropholler.dev.hermes.scraping;

import java.time.LocalDate;

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
    Integer livingAreaM2,
    Integer rooms,
    String energyLabel,
    LocalDate listedOnFundaSince,
    String status
) {}

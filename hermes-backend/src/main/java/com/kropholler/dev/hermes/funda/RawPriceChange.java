package com.kropholler.dev.hermes.funda;

import java.time.Instant;
import java.time.LocalDate;

public record RawPriceChange(
    Integer price,
    String status,
    String source,
    LocalDate date,
    Instant timestamp
) {}

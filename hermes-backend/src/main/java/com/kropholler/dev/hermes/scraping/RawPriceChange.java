package com.kropholler.dev.hermes.scraping;

import java.time.Instant;
import java.time.LocalDate;

public record RawPriceChange(
    Integer price,
    String status,
    String source,
    LocalDate date,
    Instant timestamp
) {}

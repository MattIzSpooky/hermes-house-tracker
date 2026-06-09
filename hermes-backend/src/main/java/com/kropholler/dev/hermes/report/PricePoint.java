package com.kropholler.dev.hermes.report;

import java.time.Instant;

public record PricePoint(Instant scrapedAt, Integer askingPrice) {}

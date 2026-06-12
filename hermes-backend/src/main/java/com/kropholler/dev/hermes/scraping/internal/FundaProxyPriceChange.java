package com.kropholler.dev.hermes.scraping.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

record FundaProxyPriceChange(
    @JsonProperty("price")       Integer price,
    @JsonProperty("human_price") String humanPrice,
    @JsonProperty("status")      String status,
    @JsonProperty("source")      String source,
    @JsonProperty("date")        LocalDate date,
    @JsonProperty("timestamp")   Instant timestamp
) {}

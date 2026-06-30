package com.kropholler.dev.hermes.funda.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public record FundaProxyPriceChange(
    @JsonProperty("price")       Integer price,
    @JsonProperty("human_price") String humanPrice,
    @JsonProperty("status")      String status,
    @JsonProperty("source")      String source,
    @JsonProperty("date")        LocalDate date,
    @JsonProperty("timestamp")   Instant timestamp
) {}

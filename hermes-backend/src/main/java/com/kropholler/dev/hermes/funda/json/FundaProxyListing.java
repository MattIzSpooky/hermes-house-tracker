package com.kropholler.dev.hermes.funda.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FundaProxyListing(
    @JsonProperty("global_id")            Long globalId,
    @JsonProperty("tiny_id")              String tinyId,
    @JsonProperty("url")                  String url,
    @JsonProperty("street")               String street,
    @JsonProperty("house_number")         String houseNumber,
    @JsonProperty("house_number_suffix")  String houseNumberSuffix,
    @JsonProperty("zip_code")             String zipCode,
    @JsonProperty("city")                 String city,
    @JsonProperty("province")             String province,
    @JsonProperty("asking_price")         Integer askingPrice,
    @JsonProperty("living_area_m2")       Integer livingAreaM2,
    @JsonProperty("rooms")                Integer rooms,
    @JsonProperty("bedrooms")             Integer bedrooms,
    @JsonProperty("energy_label")         String energyLabel,
    @JsonProperty("description")          String description,
    @JsonProperty("plot_area_m2")         Integer plotAreaM2,
    @JsonProperty("publication_date")     String publicationDate,
    @JsonProperty("status")              String status,
    @JsonProperty("offering_type")        String offeringType
) {}

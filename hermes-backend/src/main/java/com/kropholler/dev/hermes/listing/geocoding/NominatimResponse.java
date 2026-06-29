package com.kropholler.dev.hermes.listing.geocoding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NominatimResponse(
    String lat,
    String lon,
    List<String> boundingbox,
    @JsonProperty("place_rank") int placeRank,
    @JsonProperty("addresstype") String addressType,
    @JsonProperty("display_name") String displayName
) {}

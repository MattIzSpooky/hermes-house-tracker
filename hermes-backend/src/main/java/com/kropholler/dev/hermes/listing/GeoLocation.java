package com.kropholler.dev.hermes.listing;

public record GeoLocation(
    double latitude,
    double longitude,
    Double bboxLatMin,
    Double bboxLatMax,
    Double bboxLonMin,
    Double bboxLonMax
) {}

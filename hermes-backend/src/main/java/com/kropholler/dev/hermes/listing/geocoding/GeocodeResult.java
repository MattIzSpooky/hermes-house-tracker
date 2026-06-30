package com.kropholler.dev.hermes.listing.geocoding;

import java.util.List;

public record GeocodeResult(double lon, double lat, List<String> boundingBox) {
    public GeocodeResult {
        if (boundingBox != null && boundingBox.size() != 4) {
            throw new IllegalArgumentException("Bounding box must have exactly 4 elements");
        }
    }

    public double boundingBoxLatMin() {
        return Double.parseDouble(boundingBox.getFirst());
    }

    public double boundingBoxLatMax() {
        return Double.parseDouble(boundingBox.get(1));
    }

    public double boundingBoxLonMin() {
        return Double.parseDouble(boundingBox.get(2));
    }

    public double boundingBoxLonMax() {
        return Double.parseDouble(boundingBox.get(3));
    }
}

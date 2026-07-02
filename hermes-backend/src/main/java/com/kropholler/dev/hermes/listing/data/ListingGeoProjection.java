package com.kropholler.dev.hermes.listing.data;

import java.util.UUID;

public interface ListingGeoProjection {
    UUID getId();
    Double getLatitude();
    Double getLongitude();
    Double getBboxLatMin();
    Double getBboxLatMax();
    Double getBboxLonMin();
    Double getBboxLonMax();
}

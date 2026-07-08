package com.kropholler.dev.hermes.listing;

import lombok.Builder;

/**
 * Search criteria for a radius search around already-resolved coordinates
 * (e.g. a saved, geocoded user profile or address). See {@link ListingService#findNearLocation}.
 */
@Builder
public record ListingRadiusSearchCriteria(
        double lon,
        double lat,
        int radiusMeters,
        Integer minBedrooms,
        Integer minRooms,
        Integer minLivingAreaM2,
        String province,
        String keywords,
        Integer minPrice,
        Integer maxPrice,
        Integer limit
) {
}

package com.kropholler.dev.hermes.listing;

import lombok.Builder;

/**
 * Search criteria for {@link ListingService#findForChat}. Resolves a near-address/near-city
 * radius search internally when {@code radiusKm} and one of {@code nearAddress}/{@code nearCity}
 * are set; otherwise falls back to a plain filtered search.
 */
@Builder
public record ListingChatSearchCriteria(
        Integer minPrice,
        Integer maxPrice,
        Integer minBedrooms,
        Integer minRooms,
        Integer minLivingAreaM2,
        String province,
        String city,
        String keywords,
        boolean sortByPriceDesc,
        String nearAddress,
        String nearCity,
        Integer radiusKm,
        Integer limit
) {
    boolean hasRadiusSearch() {
        return radiusKm != null && (nearAddress != null || nearCity != null);
    }
}

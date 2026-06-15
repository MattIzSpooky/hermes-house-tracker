package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import org.springframework.data.jpa.domain.Specification;

class ListingSpecifications {

    static Specification<Listing> withParams(ListingSearchParams params) {
        Specification<Listing> spec = (root, query, cb) -> cb.conjunction();
        spec = andIfPresent(spec, "street", params.street());
        spec = andIfPresent(spec, "houseNumber", params.houseNumber());
        spec = andIfPresent(spec, "houseNumberAddition", params.houseNumberAddition());
        spec = andIfPresent(spec, "zipCode", params.zipCode());
        spec = andIfPresent(spec, "province", params.province());
        spec = andIfPresent(spec, "energyLabel", params.energyLabel());
        spec = andIfAtLeast(spec, "bedrooms", params.minBedrooms());
        spec = andIfAtLeast(spec, "rooms", params.minRooms());
        spec = andIfAtLeast(spec, "livingAreaM2", params.minLivingAreaM2());
        return spec;
    }

    private static Specification<Listing> andIfPresent(Specification<Listing> base, String field, String value) {
        if (value == null || value.isBlank()) return base;
        String pattern = "%" + value.toLowerCase() + "%";
        Specification<Listing> predicate = (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
        return base.and(predicate);
    }

    private static Specification<Listing> andIfAtLeast(Specification<Listing> base, String field, Integer value) {
        if (value == null) return base;
        return base.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
    }
}

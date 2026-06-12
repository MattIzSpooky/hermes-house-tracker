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
        return spec;
    }

    private static Specification<Listing> andIfPresent(Specification<Listing> base, String field, String value) {
        if (value == null || value.isBlank()) return base;
        String pattern = "%" + value.toLowerCase() + "%";
        Specification<Listing> predicate = (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
        return base.and(predicate);
    }
}

package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.ListingEntity;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

class ListingSpecifications {

    private ListingSpecifications() {}

    static Specification<ListingEntity> withParams(ListingSearchParams params) {
        Specification<ListingEntity> spec = (root, query, cb) -> cb.conjunction();
        spec = andIfPresent(spec, "street", params.street());
        spec = andIfPresent(spec, "houseNumber", params.houseNumber());
        spec = andIfPresent(spec, "houseNumberAddition", params.houseNumberAddition());
        spec = andIfPresent(spec, "zipCode", params.zipCode());
        spec = andIfPresent(spec, "city", params.city());
        spec = andIfPresent(spec, "province", params.province());
        spec = andIfPresent(spec, "energyLabel", params.energyLabel());
        spec = andIfAtLeast(spec, "bedrooms", params.minBedrooms());
        spec = andIfAtLeast(spec, "rooms", params.minRooms());
        spec = andIfAtLeast(spec, "livingAreaM2", params.minLivingAreaM2());
        return spec;
    }

    static Specification<ListingEntity> withParamsForRadius(ListingSearchParams params) {
        Specification<ListingEntity> spec = (root, query, cb) -> cb.conjunction();
        spec = andIfExact(spec, "city", params.city());
        spec = andIfPresent(spec, "zipCode", params.zipCode());
        spec = andIfPresent(spec, "province", params.province());
        spec = andIfPresent(spec, "energyLabel", params.energyLabel());
        spec = andIfAtLeast(spec, "bedrooms", params.minBedrooms());
        spec = andIfAtLeast(spec, "rooms", params.minRooms());
        spec = andIfAtLeast(spec, "livingAreaM2", params.minLivingAreaM2());
        return spec;
    }

    static Specification<ListingEntity> withinRadius(double lon, double lat, int radiusMeters) {
        return (root, query, cb) -> {
            String sql = String.format(Locale.US,
                "ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(%f, %f), 4326)::geography, %d)",
                lon, lat, radiusMeters);
            return cb.isTrue(((HibernateCriteriaBuilder) cb).sql(sql, Boolean.class));
        };
    }

    private static Specification<ListingEntity> andIfPresent(Specification<ListingEntity> base, String field, String value) {
        if (value == null || value.isBlank()) return base;
        String pattern = "%" + value.toLowerCase() + "%";
        Specification<ListingEntity> predicate = (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
        return base.and(predicate);
    }

    private static Specification<ListingEntity> andIfExact(Specification<ListingEntity> base, String field, String value) {
        if (value == null || value.isBlank()) return base;
        return base.and((root, query, cb) -> cb.equal(cb.lower(root.get(field)), value.toLowerCase()));
    }

    private static Specification<ListingEntity> andIfAtLeast(Specification<ListingEntity> base, String field, Integer value) {
        if (value == null) return base;
        return base.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
    }
}

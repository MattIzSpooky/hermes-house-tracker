package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {
    Optional<Listing> findByFundaId(String fundaId);
    Page<Listing> findAllByDeletedAtIsNull(Pageable pageable);
    void deleteAllByDeletedAtIsNotNull();

    @Query(value = """
            SELECT l.* FROM listings l
            LEFT JOIN LATERAL (
                SELECT phe.price
                FROM price_history_entries phe
                WHERE phe.listing_id = l.id
                ORDER BY phe.timestamp DESC
                LIMIT 1
            ) latest_price ON true
            WHERE l.deleted_at IS NULL
            AND (:minBedrooms IS NULL OR l.bedrooms >= :minBedrooms)
            AND (:minRooms IS NULL OR l.rooms >= :minRooms)
            AND (:minLivingAreaM2 IS NULL OR l.living_area_m2 >= :minLivingAreaM2)
            AND (:province IS NULL OR lower(l.province) LIKE lower(concat('%', :province, '%')))
            AND (:city IS NULL OR lower(l.city) LIKE lower(concat('%', :city, '%')))
            AND (:keywords IS NULL OR
                 to_tsvector('dutch', coalesce(l.description, '')) @@
                 plainto_tsquery('dutch', :keywords))
            AND (:minPrice IS NULL OR latest_price.price >= :minPrice)
            AND (:maxPrice IS NULL OR latest_price.price <= :maxPrice)
            ORDER BY l.last_updated_at DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Listing> searchForChat(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("city") String city,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice
    );
}

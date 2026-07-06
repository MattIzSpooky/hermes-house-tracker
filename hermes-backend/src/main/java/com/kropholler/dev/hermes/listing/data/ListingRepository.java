package com.kropholler.dev.hermes.listing.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<ListingEntity, UUID>, JpaSpecificationExecutor<ListingEntity> {
    Optional<ListingEntity> findByFundaId(String fundaId);
    List<ListingEntity> findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(
            String street, String houseNumber, String city);
    List<ListingEntity> findByStreetIgnoreCaseAndHouseNumberIgnoreCase(String street, String houseNumber);
    Page<ListingEntity> findAllByDeletedAtIsNull(Pageable pageable);
    List<ListingEntity> findByIdIn(List<UUID> ids);

    @Query(value = """
            SELECT l.id::text FROM listings l
            LEFT JOIN LATERAL (
                SELECT phe.price
                FROM price_history_entries phe
                WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
                ORDER BY phe.timestamp ASC
                LIMIT 1
            ) first_price ON true
            LEFT JOIN LATERAL (
                SELECT phe.price
                FROM price_history_entries phe
                WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
                ORDER BY phe.timestamp DESC
                LIMIT 1
            ) latest_price ON true
            WHERE l.deleted_at IS NULL
            AND first_price.price IS NOT NULL
            AND latest_price.price IS NOT NULL
            AND first_price.price > latest_price.price
            AND (:city IS NULL OR lower(l.city) LIKE lower(concat('%', :city, '%')))
            AND (CAST(first_price.price - latest_price.price AS float) / first_price.price * 100) >= :minDropPercent
            ORDER BY (CAST(first_price.price - latest_price.price AS float) / first_price.price * 100) DESC
            LIMIT 5
            """, nativeQuery = true)
    List<String> findListingIdsWithPriceDrop(
            @Param("city") String city,
            @Param("minDropPercent") double minDropPercent
    );
    void deleteAllByDeletedAtIsNotNull();

    @Modifying
    @Query(value = "UPDATE listings SET location = ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) WHERE id = :id", nativeQuery = true)
    void updateLocation(@Param("id") UUID id, @Param("lon") double lon, @Param("lat") double lat);

    @Modifying
    @Query(value = "UPDATE listings SET bounding_box = ST_MakeEnvelope(:lonMin, :latMin, :lonMax, :latMax, 4326) WHERE id = :id", nativeQuery = true)
    void updateBoundingBox(@Param("id") UUID id,
                           @Param("lonMin") double lonMin, @Param("latMin") double latMin,
                           @Param("lonMax") double lonMax, @Param("latMax") double latMax);

    @Query(value = """
            SELECT
              l.id                              AS id,
              ST_Y(l.location)                   AS latitude,
              ST_X(l.location)                   AS longitude,
              ST_YMin(l.bounding_box)             AS bboxLatMin,
              ST_YMax(l.bounding_box)             AS bboxLatMax,
              ST_XMin(l.bounding_box)             AS bboxLonMin,
              ST_XMax(l.bounding_box)             AS bboxLonMax
            FROM listings l
            WHERE l.id IN (:ids) AND l.location IS NOT NULL
            """, nativeQuery = true)
    List<ListingGeoProjection> findGeoByIds(@Param("ids") List<UUID> ids);

    @Query(value = """
            SELECT l.id::text FROM listings l
            WHERE l.deleted_at IS NULL AND l.location IS NULL
              AND l.street IS NOT NULL AND l.city IS NOT NULL
            """, nativeQuery = true)
    List<String> findIdsMissingLocation();

    @Query(value = """
            SELECT l.* FROM listings l
            LEFT JOIN LATERAL (
                SELECT phe.price
                FROM price_history_entries phe
                WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
                ORDER BY phe.timestamp DESC
                LIMIT 1
            ) latest_price ON true
            WHERE l.deleted_at IS NULL
              AND l.location IS NOT NULL
              AND ST_DWithin(
                  l.location::geography,
                  ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                  :radiusMeters
              )
              AND (:minBedrooms IS NULL OR l.bedrooms >= :minBedrooms)
              AND (:minRooms IS NULL OR l.rooms >= :minRooms)
              AND (:minLivingAreaM2 IS NULL OR l.living_area_m2 >= :minLivingAreaM2)
              AND (:province IS NULL OR lower(l.province) LIKE lower(concat('%', :province, '%')))
              AND (:keywords IS NULL OR
                   to_tsvector('dutch', coalesce(l.description, '')) @@
                   plainto_tsquery('dutch', :keywords))
              AND (:minPrice IS NULL OR latest_price.price >= :minPrice)
              AND (:maxPrice IS NULL OR latest_price.price <= :maxPrice)
            ORDER BY ST_Distance(
                l.location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            ) ASC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<ListingEntity> searchForChatNearLocation(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT l.* FROM listings l
            LEFT JOIN LATERAL (
                SELECT phe.price
                FROM price_history_entries phe
                WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
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
            ORDER BY CASE WHEN :sortDesc = true THEN -latest_price.price ELSE latest_price.price END ASC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<ListingEntity> searchForChat(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("city") String city,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("sortDesc") boolean sortDesc,
            @Param("limit") int limit
    );
}

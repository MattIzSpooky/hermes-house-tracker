package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PostGIS-based radius search in the listings table.
 *
 * These tests document a key defect: listings whose {@code location} column is NULL
 * (i.e. not yet geocoded via the async GeocodingConsumer) are silently excluded from
 * every radius search, producing 0 results even when matching listings exist in the DB.
 */
@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({
    ListingRepositoryRadiusTest.Containers.class,
    FieldEncryptor.class,
    EncryptedStringConverter.class,
    EncryptedDoubleConverter.class,
    EncryptionKeyVersionListener.class
})
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ListingRepositoryRadiusTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            );
        }
    }

    // Amsterdam city centre
    private static final double AMS_LON = 4.9041;
    private static final double AMS_LAT = 52.3676;

    // ~10 km north of Amsterdam: 10 / 111 km-per-degree ≈ 0.090°
    private static final double NORTH_10KM_LAT = AMS_LAT + 0.090;

    @Autowired ListingRepository listingRepository;
    @Autowired EntityManager em;

    private ListingEntity savedListing(String street, String city) {
        ListingEntity e = new ListingEntity();
        e.setFundaId(UUID.randomUUID().toString());
        e.setUrl("https://funda.nl/" + UUID.randomUUID());
        e.setStreet(street);
        e.setCity(city);
        return listingRepository.saveAndFlush(e);
    }

    private void geocodeListing(UUID id, double lon, double lat) {
        listingRepository.updateLocation(id, lon, lat);
        em.flush();
        em.clear();
    }

    @Test
    void withinRadius_geocodedListingAtSamePoint_isReturned() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam");
        geocodeListing(listing.getId(), AMS_LON, AMS_LAT);

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(listing.getId());
    }

    @Test
    void withinRadius_geocodedListingFarAway_isExcluded() {
        ListingEntity nearby = savedListing("Kerkstraat", "Amsterdam");
        geocodeListing(nearby.getId(), AMS_LON, AMS_LAT);

        ListingEntity farAway = savedListing("Kerkstraat", "Ver weg");
        geocodeListing(farAway.getId(), AMS_LON, NORTH_10KM_LAT);

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(nearby.getId());
    }

    /**
     * Documents the root cause of 0 results: ST_DWithin(NULL, ...) evaluates to NULL
     * which is treated as false, so any listing without its location geocoded is excluded.
     */
    @Test
    void withinRadius_listingWithNullLocation_isExcluded() {
        savedListing("Kerkstraat", "Amsterdam"); // location stays NULL — geocoding not triggered

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).isEmpty();
    }

    @Test
    void withinRadius_mixOfGeocodedNullAndOutOfRange_onlyNearbyGeocodedReturned() {
        ListingEntity geocoded = savedListing("Herengracht", "Amsterdam");
        geocodeListing(geocoded.getId(), AMS_LON, AMS_LAT);

        savedListing("Prinsengracht", "Amsterdam"); // location = NULL

        ListingEntity outOfRange = savedListing("Kerkstraat", "Ver weg");
        geocodeListing(outOfRange.getId(), AMS_LON, NORTH_10KM_LAT);

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(geocoded.getId());
    }

    @Test
    void withinRadius_listingJustInsideRadius_isReturned() {
        // ~4 km north: 4/111 ≈ 0.036° — inside 5 km radius
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam Noord");
        geocodeListing(listing.getId(), AMS_LON, AMS_LAT + 0.036);

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).hasSize(1);
    }

    @Test
    void withinRadius_listingJustOutsideRadius_isExcluded() {
        // ~6 km north: 6/111 ≈ 0.054° — outside 5 km radius
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam Ver");
        geocodeListing(listing.getId(), AMS_LON, AMS_LAT + 0.054);

        List<ListingEntity> results = listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        assertThat(results).isEmpty();
    }

    @Test
    void withinRadius_largerRadius_includesListingThatSmallerRadiusMisses() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam Ver");
        geocodeListing(listing.getId(), AMS_LON, NORTH_10KM_LAT);

        assertThat(listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000))).isEmpty();

        assertThat(listingRepository.findAll(
            ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 15_000))).hasSize(1);
    }

    @Test
    void withParamsForRadius_combinedWithWithinRadius_appliesAdditionalFilters() {
        ListingEntity inRangeWithProvince = savedListing("Kerkstraat", "Amsterdam");
        inRangeWithProvince.setProvince("Noord-Holland");
        listingRepository.saveAndFlush(inRangeWithProvince);
        geocodeListing(inRangeWithProvince.getId(), AMS_LON, AMS_LAT);

        ListingEntity inRangeNoProvince = savedListing("Damrak", "Amsterdam");
        geocodeListing(inRangeNoProvince.getId(), AMS_LON, AMS_LAT);

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, null, "Noord-Holland",
            null, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(inRangeWithProvince.getId());
    }

    @Test
    void withParamsForRadius_cityExactMatch_doesNotMatchPartialCityName() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam");
        geocodeListing(listing.getId(), AMS_LON, AMS_LAT);

        // withParamsForRadius uses exact (case-insensitive) city matching, not LIKE
        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, "Amster", null, null, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).isEmpty();
    }

    @Test
    void radius_withMinBedrooms_excludesListingsWithTooFewBedrooms() {
        ListingEntity twoBedroomsNearby = savedListing("Kerkstraat", "Amsterdam");
        twoBedroomsNearby.setBedrooms(2);
        listingRepository.saveAndFlush(twoBedroomsNearby);
        geocodeListing(twoBedroomsNearby.getId(), AMS_LON, AMS_LAT);

        ListingEntity fourBedroomsNearby = savedListing("Damrak", "Amsterdam");
        fourBedroomsNearby.setBedrooms(4);
        listingRepository.saveAndFlush(fourBedroomsNearby);
        geocodeListing(fourBedroomsNearby.getId(), AMS_LON, AMS_LAT);

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, null, null, 3, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(fourBedroomsNearby.getId());
    }

    @Test
    void radius_withMinBedrooms_outOfRangeListingExcludedEvenIfBedroomsMatch() {
        ListingEntity inRange = savedListing("Kerkstraat", "Amsterdam");
        inRange.setBedrooms(4);
        listingRepository.saveAndFlush(inRange);
        geocodeListing(inRange.getId(), AMS_LON, AMS_LAT);

        ListingEntity outOfRange = savedListing("Dorpsweg", "Ver weg");
        outOfRange.setBedrooms(4);
        listingRepository.saveAndFlush(outOfRange);
        geocodeListing(outOfRange.getId(), AMS_LON, NORTH_10KM_LAT);

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, null, null, 3, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(inRange.getId());
    }

    @Test
    void radius_withCityFilter_excludesListingsInWrongCityEvenIfNearby() {
        // Both listings are geocoded to the same point (near Amsterdam),
        // but only the one with city="Amsterdam" should survive the exact city filter.
        ListingEntity inAmsterdam = savedListing("Kerkstraat", "Amsterdam");
        geocodeListing(inAmsterdam.getId(), AMS_LON, AMS_LAT);

        ListingEntity wrongCity = savedListing("Kerkstraat", "Amstelveen");
        geocodeListing(wrongCity.getId(), AMS_LON, AMS_LAT);

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, "Amsterdam", null, null, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(inAmsterdam.getId());
    }

    @Test
    void radius_withEnergyLabel_excludesNonMatchingLabel() {
        ListingEntity labelA = savedListing("Kerkstraat", "Amsterdam");
        labelA.setEnergyLabel("A");
        listingRepository.saveAndFlush(labelA);
        geocodeListing(labelA.getId(), AMS_LON, AMS_LAT);

        ListingEntity labelC = savedListing("Damrak", "Amsterdam");
        labelC.setEnergyLabel("C");
        listingRepository.saveAndFlush(labelC);
        geocodeListing(labelC.getId(), AMS_LON, AMS_LAT);

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, null, null, null, null, null, "A", 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(labelA.getId());
    }

    @Test
    void radius_listingNotGeocoded_neverReturnedEvenIfAllFiltersMatch() {
        // Demonstrates the root cause: a listing that perfectly matches all non-spatial
        // filters is still excluded because location=NULL fails ST_DWithin.
        ListingEntity match = savedListing("Kerkstraat", "Amsterdam");
        match.setProvince("Noord-Holland");
        match.setBedrooms(4);
        listingRepository.saveAndFlush(match);
        // No geocodeListing call → location stays NULL

        ListingSearchParams params = new ListingSearchParams(
            null, null, null, null, "Amsterdam", "Noord-Holland", 3, null, null, null, 5
        );
        Specification<ListingEntity> spec = ListingSpecifications.withParamsForRadius(params)
            .and(ListingSpecifications.withinRadius(AMS_LON, AMS_LAT, 5_000));

        List<ListingEntity> results = listingRepository.findAll(spec);

        assertThat(results).isEmpty();
    }
}

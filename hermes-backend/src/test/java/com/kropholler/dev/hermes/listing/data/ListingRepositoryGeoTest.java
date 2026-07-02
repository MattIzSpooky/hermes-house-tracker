package com.kropholler.dev.hermes.listing.data;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ListingRepositoryGeoTest.Containers.class)
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ListingRepositoryGeoTest {

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

    private void geocode(UUID id, double lon, double lat) {
        listingRepository.updateLocation(id, lon, lat);
        listingRepository.updateBoundingBox(id, lon - 0.001, lat - 0.001, lon + 0.001, lat + 0.001);
        em.flush();
        em.clear();
    }

    @Test
    void findGeoByIds_geocodedListing_returnsLatLonAndBoundingBox() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam");
        geocode(listing.getId(), 4.9041, 52.3676);

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(List.of(listing.getId()));

        assertThat(result).hasSize(1);
        ListingGeoProjection projection = result.get(0);
        assertThat(projection.getId()).isEqualTo(listing.getId());
        assertThat(projection.getLatitude()).isEqualTo(52.3676, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getLongitude()).isEqualTo(4.9041, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLatMin()).isEqualTo(52.3666, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLatMax()).isEqualTo(52.3686, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLonMin()).isEqualTo(4.9031, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLonMax()).isEqualTo(4.9051, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void findGeoByIds_listingWithoutLocation_isExcludedFromResults() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam"); // never geocoded

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(List.of(listing.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void findGeoByIds_mixOfGeocodedAndNot_returnsOnlyGeocoded() {
        ListingEntity geocoded = savedListing("Kerkstraat", "Amsterdam");
        geocode(geocoded.getId(), 4.9041, 52.3676);
        ListingEntity notGeocoded = savedListing("Damrak", "Amsterdam");

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(
            List.of(geocoded.getId(), notGeocoded.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(geocoded.getId());
    }

    @Test
    void findIdsMissingLocation_returnsOnlyListingsWithoutLocation() {
        ListingEntity geocoded = savedListing("Kerkstraat", "Amsterdam");
        geocode(geocoded.getId(), 4.9041, 52.3676);
        ListingEntity notGeocoded = savedListing("Damrak", "Amsterdam");

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).containsExactly(notGeocoded.getId().toString());
    }

    @Test
    void findIdsMissingLocation_deletedListing_isExcluded() {
        ListingEntity deleted = savedListing("Kerkstraat", "Amsterdam");
        deleted.setDeletedAt(java.time.Instant.now());
        listingRepository.saveAndFlush(deleted);

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).isEmpty();
    }

    @Test
    void findIdsMissingLocation_missingStreetOrCity_isExcluded() {
        ListingEntity noStreet = new ListingEntity();
        noStreet.setFundaId(UUID.randomUUID().toString());
        noStreet.setUrl("https://funda.nl/" + UUID.randomUUID());
        noStreet.setCity("Amsterdam"); // street left null
        listingRepository.saveAndFlush(noStreet);

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).isEmpty();
    }
}

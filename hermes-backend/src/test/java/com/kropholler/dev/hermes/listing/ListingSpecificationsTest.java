package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ListingSpecifications} using an in-memory H2 database.
 *
 * Covers {@code withParams} (used for regular search) and {@code withParamsForRadius}
 * (used as the non-spatial pre-filter in radius searches). PostGIS is not required here;
 * see {@link ListingRepositoryRadiusTest} for the spatial {@code withinRadius} tests.
 */
@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class})
class ListingSpecificationsTest {

    @Autowired ListingRepository listingRepository;

    // ── helpers ──────────────────────────────────────────────────────────────

    private ListingEntity listing(String street, String houseNumber, String zipCode,
                                  String city, String province, String energyLabel,
                                  Integer bedrooms, Integer rooms, Integer livingAreaM2) {
        ListingEntity e = new ListingEntity();
        e.setFundaId(UUID.randomUUID().toString());
        e.setUrl("https://funda.nl/" + UUID.randomUUID());
        e.setStreet(street);
        e.setHouseNumber(houseNumber);
        e.setZipCode(zipCode);
        e.setCity(city);
        e.setProvince(province);
        e.setEnergyLabel(energyLabel);
        e.setBedrooms(bedrooms);
        e.setRooms(rooms);
        e.setLivingAreaM2(livingAreaM2);
        return listingRepository.save(e);
    }

    private List<ListingEntity> find(Specification<ListingEntity> spec) {
        return listingRepository.findAll(spec);
    }

    // ── withParams: street ────────────────────────────────────────────────────

    @Test
    void withParams_street_partialMatchReturnsListing() {
        listing("Kerkstraat", "13", null, null, null, null, null, null, null);
        listing("Dorpsweg", "5", null, null, null, null, null, null, null);

        var params = new ListingSearchParams("Kerk", null, null, null, null, null, null, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStreet()).isEqualTo("Kerkstraat");
    }

    @Test
    void withParams_street_caseInsensitive() {
        listing("Kerkstraat", null, null, null, null, null, null, null, null);

        var params = new ListingSearchParams("KERKSTRAAT", null, null, null, null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(1);
    }

    @Test
    void withParams_street_noMatch_returnsEmpty() {
        listing("Kerkstraat", null, null, null, null, null, null, null, null);

        var params = new ListingSearchParams("Dorpsweg", null, null, null, null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).isEmpty();
    }

    @Test
    void withParams_street_blankValue_isIgnoredAndReturnsAll() {
        listing("Kerkstraat", null, null, null, null, null, null, null, null);
        listing("Dorpsweg", null, null, null, null, null, null, null, null);

        var params = new ListingSearchParams("  ", null, null, null, null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParams: houseNumber ───────────────────────────────────────────────

    @Test
    void withParams_houseNumber_partialMatchReturnsMultiple() {
        listing("Kerkstraat", "10", null, null, null, null, null, null, null);
        listing("Kerkstraat", "100", null, null, null, null, null, null, null);
        listing("Kerkstraat", "5", null, null, null, null, null, null, null);

        var params = new ListingSearchParams(null, "10", null, null, null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParams: zipCode ───────────────────────────────────────────────────

    @Test
    void withParams_zipCode_partialMatch() {
        listing(null, null, "1234AB", null, null, null, null, null, null);
        listing(null, null, "5678CD", null, null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, "1234", null, null, null, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getZipCode()).isEqualTo("1234AB");
    }

    @Test
    void withParams_zipCode_caseInsensitive() {
        listing(null, null, "1234AB", null, null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, "1234ab", null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(1);
    }

    // ── withParams: city ──────────────────────────────────────────────────────

    @Test
    void withParams_city_partialMatch() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);
        listing(null, null, null, "Rotterdam", null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amster", null, null, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCity()).isEqualTo("Amsterdam");
    }

    @Test
    void withParams_city_caseInsensitive() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, "amsterdam", null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(1);
    }

    // ── withParams: province ──────────────────────────────────────────────────

    @Test
    void withParams_province_partialMatch() {
        listing(null, null, null, null, "Noord-Holland", null, null, null, null);
        listing(null, null, null, null, "Zuid-Holland", null, null, null, null);
        listing(null, null, null, null, "Utrecht", null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, "Holland", null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParams: energyLabel ───────────────────────────────────────────────

    @Test
    void withParams_energyLabel_exactLikeMatch() {
        listing(null, null, null, null, null, "A", null, null, null);
        listing(null, null, null, null, null, "A+", null, null, null);
        listing(null, null, null, null, null, "B", null, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, "A", null);
        // LIKE '%A%' matches "A" and "A+"
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParams: minBedrooms ───────────────────────────────────────────────

    @Test
    void withParams_minBedrooms_returnsListingsWithAtLeastThatMany() {
        listing(null, null, null, null, null, null, 2, null, null);
        listing(null, null, null, null, null, null, 3, null, null);
        listing(null, null, null, null, null, null, 4, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, 3, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getBedrooms() >= 3);
    }

    @Test
    void withParams_minBedrooms_excludesListingsBelowThreshold() {
        listing(null, null, null, null, null, null, 1, null, null);
        listing(null, null, null, null, null, null, 2, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, 3, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).isEmpty();
    }

    @Test
    void withParams_minBedrooms_null_doesNotFilter() {
        listing(null, null, null, null, null, null, 1, null, null);
        listing(null, null, null, null, null, null, 5, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParams: minRooms ──────────────────────────────────────────────────

    @Test
    void withParams_minRooms_returnsListingsWithAtLeastThatMany() {
        listing(null, null, null, null, null, null, null, 2, null);
        listing(null, null, null, null, null, null, null, 4, null);
        listing(null, null, null, null, null, null, null, 6, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, null, 4, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getRooms() >= 4);
    }

    // ── withParams: minLivingAreaM2 ───────────────────────────────────────────

    @Test
    void withParams_minLivingAreaM2_returnsListingsWithAtLeastThatSize() {
        listing(null, null, null, null, null, null, null, null, 60);
        listing(null, null, null, null, null, null, null, null, 100);
        listing(null, null, null, null, null, null, null, null, 150);

        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, 100, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getLivingAreaM2() >= 100);
    }

    // ── withParams: combined (AND) ────────────────────────────────────────────

    @Test
    void withParams_multipleFilters_allMustMatch() {
        listing("Kerkstraat", null, null, "Amsterdam", "Noord-Holland", null, 3, null, null);
        listing("Kerkstraat", null, null, "Rotterdam", "Zuid-Holland", null, 3, null, null);
        listing("Dorpsweg", null, null, "Amsterdam", "Noord-Holland", null, 3, null, null);

        var params = new ListingSearchParams("Kerkstraat", null, null, null, "Amsterdam", null, null, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        // Only the listing in Amsterdam on Kerkstraat
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCity()).isEqualTo("Amsterdam");
        assertThat(results.get(0).getStreet()).isEqualTo("Kerkstraat");
    }

    @Test
    void withParams_cityAndMinBedrooms_bothApplied() {
        listing(null, null, null, "Amsterdam", null, null, 2, null, null);
        listing(null, null, null, "Amsterdam", null, null, 4, null, null);
        listing(null, null, null, "Rotterdam", null, null, 4, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, 3, null, null, null, null);
        List<ListingEntity> results = find(ListingSpecifications.withParams(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCity()).isEqualTo("Amsterdam");
        assertThat(results.get(0).getBedrooms()).isEqualTo(4);
    }

    @Test
    void withParams_allNullFields_returnsAllListings() {
        listing("Kerkstraat", null, null, "Amsterdam", null, null, null, null, null);
        listing("Dorpsweg", null, null, "Rotterdam", null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null);
        // isEmpty() is true → service uses simple findAll, but if spec is built it matches all
        assertThat(find(ListingSpecifications.withParams(params))).hasSize(2);
    }

    // ── withParamsForRadius: city uses exact match (not LIKE) ─────────────────

    @Test
    void withParamsForRadius_city_exactMatchRequired() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, null, null, 5);
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(1);
    }

    @Test
    void withParamsForRadius_city_partialMatchIsNotSufficient() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);

        // withParams would match "Amster" via LIKE; withParamsForRadius requires exact equality
        var params = new ListingSearchParams(null, null, null, null, "Amster", null, null, null, null, null, 5);
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).isEmpty();
    }

    @Test
    void withParamsForRadius_city_caseInsensitiveExactMatch() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, "amsterdam", null, null, null, null, null, 5);
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(1);
    }

    @Test
    void withParamsForRadius_city_null_doesNotFilter() {
        listing(null, null, null, "Amsterdam", null, null, null, null, null);
        listing(null, null, null, "Rotterdam", null, null, null, null, null);

        var params = new ListingSearchParams("Kerkstraat", null, null, null, null, null, null, null, null, null, 5);
        // city is null → no city filter applied → both listings pass
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(2);
    }

    @Test
    void withParamsForRadius_streetIsNotAppliedAsFilter() {
        // withParamsForRadius intentionally excludes the street field:
        // radius search returns ALL nearby listings regardless of street name
        listing("Kerkstraat", null, null, "Amsterdam", null, null, null, null, null);
        listing("Damrak", null, null, "Amsterdam", null, null, null, null, null);

        var params = new ListingSearchParams("Kerkstraat", null, null, null, "Amsterdam", null, null, null, null, null, 5);
        // Both listings match even though only one is on "Kerkstraat"
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(2);
    }

    @Test
    void withParamsForRadius_province_usesLikeMatchNotExact() {
        listing(null, null, null, null, "Noord-Holland", null, null, null, null);
        listing(null, null, null, null, "Utrecht", null, null, null, null);

        var params = new ListingSearchParams(null, null, null, null, null, "Noord", null, null, null, null, 5);
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(1);
    }

    @Test
    void withParamsForRadius_zipCode_partialMatch() {
        listing(null, null, "1234AB", null, null, null, null, null, null);
        listing(null, null, "5678CD", null, null, null, null, null, null);

        var params = new ListingSearchParams(null, null, null, "1234", null, null, null, null, null, null, 5);
        assertThat(find(ListingSpecifications.withParamsForRadius(params))).hasSize(1);
    }

    @Test
    void withParamsForRadius_minBedrooms_filters() {
        listing(null, null, null, "Amsterdam", null, null, 2, null, null);
        listing(null, null, null, "Amsterdam", null, null, 4, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, 3, null, null, null, 5);
        List<ListingEntity> results = find(ListingSpecifications.withParamsForRadius(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBedrooms()).isEqualTo(4);
    }

    @Test
    void withParamsForRadius_minLivingAreaM2_filters() {
        listing(null, null, null, "Amsterdam", null, null, null, null, 60);
        listing(null, null, null, "Amsterdam", null, null, null, null, 120);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, 100, null, 5);
        List<ListingEntity> results = find(ListingSpecifications.withParamsForRadius(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLivingAreaM2()).isEqualTo(120);
    }

    @Test
    void withParamsForRadius_energyLabel_filters() {
        listing(null, null, null, "Amsterdam", null, "A", null, null, null);
        listing(null, null, null, "Amsterdam", null, "C", null, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, null, null, null, "A", 5);
        List<ListingEntity> results = find(ListingSpecifications.withParamsForRadius(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEnergyLabel()).isEqualTo("A");
    }

    @Test
    void withParamsForRadius_cityAndMinBedrooms_bothApplied() {
        listing(null, null, null, "Amsterdam", null, null, 2, null, null);
        listing(null, null, null, "Amsterdam", null, null, 4, null, null);
        listing(null, null, null, "Rotterdam", null, null, 4, null, null);

        var params = new ListingSearchParams(null, null, null, null, "Amsterdam", null, 3, null, null, null, 5);
        List<ListingEntity> results = find(ListingSpecifications.withParamsForRadius(params));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCity()).isEqualTo("Amsterdam");
        assertThat(results.get(0).getBedrooms()).isEqualTo(4);
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    void withParams_pagination_returnsCorrectPage() {
        for (int i = 0; i < 5; i++) {
            listing("Kerkstraat", String.valueOf(i), null, null, null, null, null, null, null);
        }

        var params = new ListingSearchParams("Kerkstraat", null, null, null, null, null, null, null, null, null, null);
        Page<ListingEntity> page = listingRepository.findAll(
            ListingSpecifications.withParams(params), PageRequest.of(0, 3));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    void withParams_secondPage_returnsRemainingResults() {
        for (int i = 0; i < 5; i++) {
            listing("Kerkstraat", String.valueOf(i), null, null, null, null, null, null, null);
        }

        var params = new ListingSearchParams("Kerkstraat", null, null, null, null, null, null, null, null, null, null);
        Page<ListingEntity> page = listingRepository.findAll(
            ListingSpecifications.withParams(params), PageRequest.of(1, 3));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(2);
    }
}

package com.kropholler.dev.hermes.listing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListingSearchParamsTest {

    // ── isEmpty() ─────────────────────────────────────────────────────────────

    @Test
    void isEmpty_allNullOrBlank_returnsTrue() {
        assertThat(params(null, null, null, null, null, null, null, null, null, null, null).isEmpty()).isTrue();
    }

    @Test
    void isEmpty_blankStrings_returnsTrue() {
        assertThat(params("  ", "  ", "  ", "  ", "  ", "  ", null, null, null, "  ", null).isEmpty()).isTrue();
    }

    @Test
    void isEmpty_streetNonBlank_returnsFalse() {
        assertThat(params("Kerkstraat", null, null, null, null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_houseNumberNonBlank_returnsFalse() {
        // street is blank → evaluates houseNumber next; houseNumber non-blank → short-circuits to false
        assertThat(params(null, "13", null, null, null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_houseNumberAdditionNonBlank_returnsFalse() {
        assertThat(params(null, null, "A", null, null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_zipCodeNonBlank_returnsFalse() {
        assertThat(params(null, null, null, "1234AB", null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_cityNonBlank_returnsFalse() {
        assertThat(params(null, null, null, null, "Amsterdam", null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_provinceNonBlank_returnsFalse() {
        assertThat(params(null, null, null, null, null, "Noord-Holland", null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_minBedroomsNonNull_returnsFalse() {
        assertThat(params(null, null, null, null, null, null, 3, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_minRoomsNonNull_returnsFalse() {
        assertThat(params(null, null, null, null, null, null, null, 4, null, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_minLivingAreaM2NonNull_returnsFalse() {
        assertThat(params(null, null, null, null, null, null, null, null, 80, null, null).isEmpty()).isFalse();
    }

    @Test
    void isEmpty_energyLabelNonBlank_returnsFalse() {
        // All fields blank/null except energyLabel → reaches line 20 and short-circuits there
        assertThat(params(null, null, null, null, null, null, null, null, null, "A", null).isEmpty()).isFalse();
    }

    // ── hasRadiusSearch() ─────────────────────────────────────────────────────

    @Test
    void hasRadiusSearch_streetAndRadiusKmSet_returnsTrue() {
        assertThat(params("Kerkstraat", null, null, null, null, null, null, null, null, null, 5).hasRadiusSearch()).isTrue();
    }

    @Test
    void hasRadiusSearch_cityAndRadiusKmSet_returnsTrue() {
        assertThat(params(null, null, null, null, "Amsterdam", null, null, null, null, null, 5).hasRadiusSearch()).isTrue();
    }

    @Test
    void hasRadiusSearch_radiusKmNull_returnsFalse() {
        assertThat(params("Kerkstraat", null, null, null, "Amsterdam", null, null, null, null, null, null).hasRadiusSearch()).isFalse();
    }

    @Test
    void hasRadiusSearch_radiusKmSetButNoStreetOrCity_returnsFalse() {
        assertThat(params(null, null, null, null, null, null, null, null, null, null, 5).hasRadiusSearch()).isFalse();
    }

    @Test
    void hasRadiusSearch_blankStreetAndBlankCity_returnsFalse() {
        assertThat(params("  ", null, null, null, "  ", null, null, null, null, null, 5).hasRadiusSearch()).isFalse();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static ListingSearchParams params(
            String street, String houseNumber, String houseNumberAddition,
            String zipCode, String city, String province,
            Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
            String energyLabel, Integer radiusKm) {
        return new ListingSearchParams(street, houseNumber, houseNumberAddition,
                zipCode, city, province, minBedrooms, minRooms, minLivingAreaM2,
                energyLabel, radiusKm);
    }
}

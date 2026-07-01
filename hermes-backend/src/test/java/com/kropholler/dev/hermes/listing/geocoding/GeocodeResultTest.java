package com.kropholler.dev.hermes.listing.geocoding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeocodeResultTest {

    @Test
    void constructor_withNullBoundingBox_isAllowed() {
        GeocodeResult result = new GeocodeResult(4.9041, 52.3676, null);
        assertThat(result.boundingBox()).isNull();
    }

    @Test
    void constructor_withFourElementBoundingBox_isAllowed() {
        List<String> bb = List.of("52.30", "52.40", "4.80", "5.00");
        GeocodeResult result = new GeocodeResult(4.9, 52.35, bb);
        assertThat(result.boundingBox()).hasSize(4);
    }

    @Test
    void constructor_withWrongSizeBoundingBox_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new GeocodeResult(4.9, 52.3, List.of("52.30", "52.40", "4.80")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("4");
    }
}

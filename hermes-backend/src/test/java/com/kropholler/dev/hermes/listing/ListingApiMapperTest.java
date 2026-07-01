package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.openapi.ListingDetailResponse;
import com.kropholler.dev.hermes.listing.openapi.ListingSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListingApiMapperTest {

    private final ListingApiMapper mapper = Mappers.getMapper(ListingApiMapper.class);

    private ListingDto dto(UUID id) {
        return new ListingDto(id, "funda-1", "https://funda.nl/1",
            "Dorpstraat", "10", "A", "1234AB", "Utrecht", "Utrecht",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z"),
            350000, ListingStatus.FOR_SALE, "Nice house", 90, 5, 3, "B", 120);
    }

    @Test
    void toSummaryResponse_mapsCurrentPriceToAskingPrice() {
        ListingDto dto = dto(UUID.randomUUID());

        ListingSummaryResponse response = mapper.toSummaryResponse(dto);

        assertThat(response.getAskingPrice()).isEqualTo(350000);
    }

    @Test
    void toSummaryResponse_doesNotIncludeIgnoredFields() {
        ListingDto dto = dto(UUID.randomUUID());

        ListingSummaryResponse response = mapper.toSummaryResponse(dto);

        assertThat(response.getStreet()).isEqualTo("Dorpstraat");
        assertThat(response.getCity()).isEqualTo("Utrecht");
        assertThat(response.getStatus()).isEqualTo("FOR_SALE");
    }

    @Test
    void toSummaryResponse_nullStatus_producesNullStatus() {
        ListingDto dto = new ListingDto(UUID.randomUUID(), "f", "u",
            "S", "1", null, "Z", "C", "P",
            Instant.now(), Instant.now(), null, null, null, null, null, null, null, null);

        ListingSummaryResponse response = mapper.toSummaryResponse(dto);

        assertThat(response.getStatus()).isNull();
    }

    @Test
    void toDetailResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        ListingDto dto = dto(id);

        ListingDetailResponse response = mapper.toDetailResponse(dto);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStreet()).isEqualTo("Dorpstraat");
        assertThat(response.getCurrentPrice()).isEqualTo(350000);
        assertThat(response.getStatus()).isEqualTo("FOR_SALE");
        assertThat(response.getFirstSeenAt()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}

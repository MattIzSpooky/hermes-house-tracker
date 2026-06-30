package com.kropholler.dev.hermes.favourites;

import com.kropholler.dev.hermes.favourites.openapi.FavouriteResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FavouriteApiMapperTest {

    private final FavouriteApiMapper mapper = Mappers.getMapper(FavouriteApiMapper.class);

    @Test
    void toResponse_mapsSavedAtToUtcOffsetDateTime() {
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-03-10T14:30:00Z");
        FavouriteDto dto = new FavouriteDto(listingId, savedAt);

        FavouriteResponse response = mapper.toResponse(dto);

        assertThat(response.getListingId()).isEqualTo(listingId);
        assertThat(response.getSavedAt()).isEqualTo(OffsetDateTime.of(2026, 3, 10, 14, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toResponse_nullSavedAt_producesNullInResponse() {
        FavouriteDto dto = new FavouriteDto(UUID.randomUUID(), null);

        FavouriteResponse response = mapper.toResponse(dto);

        assertThat(response.getSavedAt()).isNull();
    }
}

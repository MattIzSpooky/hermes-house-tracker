package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteApiMapperTest {

    private final FavoriteApiMapper mapper = Mappers.getMapper(FavoriteApiMapper.class);

    @Test
    void toResponse_mapsSavedAtToUtcOffsetDateTime() {
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-03-10T14:30:00Z");
        FavoriteDto dto = new FavoriteDto(listingId, savedAt);

        FavoriteResponse response = mapper.toResponse(dto);

        assertThat(response.getListingId()).isEqualTo(listingId);
        assertThat(response.getSavedAt()).isEqualTo(OffsetDateTime.of(2026, 3, 10, 14, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toResponse_nullSavedAt_producesNullInResponse() {
        FavoriteDto dto = new FavoriteDto(UUID.randomUUID(), null);

        FavoriteResponse response = mapper.toResponse(dto);

        assertThat(response.getSavedAt()).isNull();
    }
}

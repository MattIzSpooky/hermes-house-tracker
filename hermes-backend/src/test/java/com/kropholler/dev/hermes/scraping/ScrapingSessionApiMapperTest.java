package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapingSessionApiMapperTest {

    private final ScrapingSessionApiMapper mapper = Mappers.getMapper(ScrapingSessionApiMapper.class);

    @Test
    void toResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-10T10:00:00Z");
        Instant completedAt = Instant.parse("2026-04-10T10:05:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            id, ScrapingSessionStatus.COMPLETED, ScrapingSessionType.SEARCH, createdAt, completedAt);

        ScrapingSessionResponse response = mapper.toResponse(dto);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 4, 10, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(response.getCompletedAt()).isEqualTo(OffsetDateTime.of(2026, 4, 10, 10, 5, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toResponse_nullCompletedAt_producesNullInResponse() {
        ScrapingSessionDto dto = new ScrapingSessionDto(
            UUID.randomUUID(), ScrapingSessionStatus.PENDING, ScrapingSessionType.SEARCH,
            Instant.now(), null);

        ScrapingSessionResponse response = mapper.toResponse(dto);

        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}

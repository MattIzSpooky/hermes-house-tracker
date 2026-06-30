package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RescrapeMapperTest {

    private final RescrapeMapper mapper = Mappers.getMapper(RescrapeMapper.class);

    @Test
    void toResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-15T09:00:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            id, ScrapingSessionStatus.PENDING, ScrapingSessionType.RESCRAPE, createdAt, null);

        ScrapingSessionResponse response = mapper.toResponse(dto);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 3, 15, 9, 0, 0, 0, ZoneOffset.UTC));
        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}

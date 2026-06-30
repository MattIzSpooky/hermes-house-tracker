package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
public interface RescrapeMapper {

    ScrapingSessionResponse toResponse(ScrapingSessionDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}

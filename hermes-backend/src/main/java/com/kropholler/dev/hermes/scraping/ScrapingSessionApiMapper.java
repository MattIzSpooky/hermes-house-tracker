package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
public interface ScrapingSessionApiMapper {

    ScrapingSessionResponse toResponse(ScrapingSessionDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}

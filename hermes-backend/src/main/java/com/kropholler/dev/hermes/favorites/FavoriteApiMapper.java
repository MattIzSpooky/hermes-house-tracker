package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
public interface FavoriteApiMapper {

    FavoriteResponse toResponse(FavoriteDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}

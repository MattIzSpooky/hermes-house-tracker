package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
interface NotificationApiMapper {

    @Mapping(target = "listingIds", defaultExpression = "java(java.util.List.of())")
    NotificationResponse toResponse(NotificationDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}

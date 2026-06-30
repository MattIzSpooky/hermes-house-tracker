package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
public interface ListingApiMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {
        "fundaId", "url", "lastSeenAt", "description",
        "livingAreaM2", "plotAreaM2", "rooms", "bedrooms", "energyLabel"
    })
    @Mapping(target = "askingPrice", source = "currentPrice")
    ListingSummaryResponse toSummaryResponse(ListingDto dto);

    ListingDetailResponse toDetailResponse(ListingDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    default String toStatusName(ListingStatus status) {
        return status != null ? status.name() : null;
    }
}

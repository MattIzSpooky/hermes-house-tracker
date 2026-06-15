package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.api.generated.model.ListingDetailResponse;
import com.kropholler.dev.hermes.api.generated.model.ListingReportResponse;
import com.kropholler.dev.hermes.api.generated.model.ListingSummaryResponse;
import com.kropholler.dev.hermes.api.generated.model.PricePointResponse;
import com.kropholler.dev.hermes.api.generated.model.ScrapingSessionResponse;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.report.ListingReport;
import com.kropholler.dev.hermes.report.PricePoint;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
interface ApiMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {
        "fundaId", "url", "lastSeenAt", "description",
        "livingAreaM2", "plotAreaM2", "rooms", "bedrooms", "energyLabel"
    })
    @Mapping(target = "askingPrice", source = "currentPrice")
    ListingSummaryResponse toSummaryResponse(ListingDto dto);

    ListingDetailResponse toDetailResponse(ListingDto dto);

    ListingReportResponse toReportResponse(ListingReport report);

    PricePointResponse toPricePointResponse(PricePoint point);

    ScrapingSessionResponse toSessionResponse(ScrapingSessionDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    default String toStatusName(ListingStatus status) {
        return status != null ? status.name() : null;
    }
}

package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.report.openapi.ListingReportResponse;
import com.kropholler.dev.hermes.report.openapi.PricePointResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.ListingStatus;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
public interface ReportApiMapper {

    ListingReportResponse toReportResponse(ListingReport report);

    PricePointResponse toPricePointResponse(PricePoint point);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    default String toStatusName(ListingStatus status) {
        return status != null ? status.name() : null;
    }
}

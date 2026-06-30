package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.report.openapi.ListingReportResponse;
import com.kropholler.dev.hermes.report.openapi.PricePointResponse;
import com.kropholler.dev.hermes.listing.ListingStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportApiMapperTest {

    private final ReportApiMapper mapper = Mappers.getMapper(ReportApiMapper.class);

    @Test
    void toReportResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.parse("2026-04-01T09:00:00Z");
        ListingReport report = new ListingReport(
            id, 45L, 320000, 340000, -5.9,
            List.of(new PricePoint(ts, 340000), new PricePoint(ts.plusSeconds(86400), 320000)),
            ListingStatus.FOR_SALE
        );

        ListingReportResponse response = mapper.toReportResponse(report);

        assertThat(response.getListingId()).isEqualTo(id);
        assertThat(response.getDaysInHermes()).isEqualTo(45L);
        assertThat(response.getCurrentPrice()).isEqualTo(320000);
        assertThat(response.getInitialPrice()).isEqualTo(340000);
        assertThat(response.getPriceChangePct()).isEqualTo(-5.9);
        assertThat(response.getCurrentStatus()).isEqualTo("FOR_SALE");
        assertThat(response.getPriceHistory()).hasSize(2);
    }

    @Test
    void toStatusName_returnsNullForNullStatus() {
        assertThat(mapper.toStatusName(null)).isNull();
    }

    @Test
    void toStatusName_returnsEnumName() {
        assertThat(mapper.toStatusName(ListingStatus.SOLD)).isEqualTo("SOLD");
    }

    @Test
    void toPricePointResponse_mapsTimestampToUtc() {
        Instant ts = Instant.parse("2026-05-20T12:00:00Z");
        PricePoint point = new PricePoint(ts, 295000);

        PricePointResponse response = mapper.toPricePointResponse(point);

        assertThat(response.getPrice()).isEqualTo(295000);
        assertThat(response.getTimestamp()).isEqualTo(OffsetDateTime.of(2026, 5, 20, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}

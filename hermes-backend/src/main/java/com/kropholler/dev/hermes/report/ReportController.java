package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.report.openapi.ListingReportResponse;
import com.kropholler.dev.hermes.report.openapi.ReportsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;
    private final ReportApiMapper reportApiMapper;

    @Override
    public ResponseEntity<ListingReportResponse> getListingReport(UUID id) {
        ListingReport report = reportService.generateReport(id);
        return ResponseEntity.ok(reportApiMapper.toReportResponse(report));
    }
}

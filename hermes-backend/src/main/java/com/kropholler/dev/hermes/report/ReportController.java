package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.report.openapi.ListingReportResponse;
import com.kropholler.dev.hermes.report.openapi.ReportsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;
    private final ReportApiMapper reportApiMapper;

    @Override
    public ResponseEntity<ListingReportResponse> getListingReport(UUID id) {
        return reportService.generateReport(id)
            .map(report -> ResponseEntity.ok(reportApiMapper.toReportResponse(report)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }
}

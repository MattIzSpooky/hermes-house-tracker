package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.api.generated.ListingsApi;
import com.kropholler.dev.hermes.api.generated.model.*;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingSearchParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.report.ListingReport;
import com.kropholler.dev.hermes.report.ReportService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class ListingController implements ListingsApi {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;
    private final ReportService reportService;
    private final ListingSummaryService summaryService;

    @Override
    public ResponseEntity<ListingPage> getListings(Integer page, Integer size,
            String street, String houseNumber, String houseNumberAddition,
            String zipCode, String province) {
        ListingSearchParams params = new ListingSearchParams(street, houseNumber, houseNumberAddition, zipCode, province, null, null, null, null);
        Page<ListingDto> result = listingService.findAll(params, PageRequest.of(page, size));
        ListingPage response = new ListingPage()
            .content(result.getContent().stream().map(this::toSummaryResponse).toList())
            .totalElements(result.getTotalElements())
            .totalPages(result.getTotalPages())
            .page(result.getNumber())
            .size(result.getSize());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ListingDetailResponse> getListing(UUID id) {
        return listingService.findById(id)
            .map(dto -> ResponseEntity.ok(toDetailResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }

    @Override
    public ResponseEntity<ScrapingSessionResponse> rescrapeListing(UUID id) {
        ListingDto listing = listingService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
        ScrapingSessionDto session = queueService.enqueueRescrape(listing.url(), listing.city());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toSessionResponse(session));
    }

    @Override
    public ResponseEntity<ListingReportResponse> getListingReport(UUID id) {
        return reportService.generateReport(id)
            .map(report -> ResponseEntity.ok(toReportResponse(report)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }

    @Override
    public ResponseEntity<AiSummaryResponse> getListingSummary(UUID id) {
        return summaryService.findByListingId(id)
            .map(dto -> ResponseEntity.ok(new AiSummaryResponse()
                .listingId(dto.listingId())
                .summary(dto.summary())
                .generatedAt(dto.generatedAt().atOffset(ZoneOffset.UTC))))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Summary for listing " + id + " not found"));
    }

    private ListingSummaryResponse toSummaryResponse(ListingDto dto) {
        return new ListingSummaryResponse()
            .id(dto.id())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .askingPrice(dto.currentPrice())
            .status(dto.status() != null ? dto.status().name() : null)
            .firstSeenAt(dto.firstSeenAt().atOffset(ZoneOffset.UTC));
    }

    private ListingDetailResponse toDetailResponse(ListingDto dto) {
        return new ListingDetailResponse()
            .id(dto.id())
            .fundaId(dto.fundaId())
            .url(dto.url())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .firstSeenAt(dto.firstSeenAt().atOffset(ZoneOffset.UTC))
            .lastSeenAt(dto.lastSeenAt().atOffset(ZoneOffset.UTC))
            .currentPrice(dto.currentPrice())
            .status(dto.status() != null ? dto.status().name() : null);
    }

    private ListingReportResponse toReportResponse(ListingReport r) {
        return new ListingReportResponse()
            .listingId(r.listingId())
            .daysInHermes(r.daysInHermes())
            .currentPrice(r.currentPrice())
            .initialPrice(r.initialPrice())
            .priceChangePct(r.priceChangePct())
            .priceHistory(r.priceHistory().stream()
                .map(p -> new PricePointResponse()
                    .timestamp(p.timestamp().atOffset(ZoneOffset.UTC))
                    .price(p.price()))
                .toList())
            .currentStatus(r.currentStatus() != null ? r.currentStatus().name() : null);
    }

    private ScrapingSessionResponse toSessionResponse(ScrapingSessionDto dto) {
        return new ScrapingSessionResponse()
            .id(dto.id())
            .status(ScrapingSessionResponse.StatusEnum.valueOf(dto.status().name()))
            .type(ScrapingSessionResponse.TypeEnum.valueOf(dto.type().name()))
            .createdAt(dto.createdAt().atOffset(ZoneOffset.UTC))
            .completedAt(dto.completedAt() != null ? dto.completedAt().atOffset(ZoneOffset.UTC) : null);
    }
}

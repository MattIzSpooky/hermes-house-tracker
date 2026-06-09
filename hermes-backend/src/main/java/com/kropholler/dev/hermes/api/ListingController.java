package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.api.generated.ListingsApi;
import com.kropholler.dev.hermes.api.generated.model.*;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
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
    public ResponseEntity<ListingPage> getListings(Integer page, Integer size) {
        Page<ListingDto> result = listingService.findAll(PageRequest.of(page, size));
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
        ListingSnapshotDto snap = dto.latestSnapshot();
        return new ListingSummaryResponse()
            .id(dto.id())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .askingPrice(snap != null ? snap.askingPrice() : null)
            .status(snap != null && snap.status() != null ? snap.status().name() : null)
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
            .latestSnapshot(dto.latestSnapshot() != null ? toSnapshotResponse(dto.latestSnapshot()) : null);
    }

    private SnapshotResponse toSnapshotResponse(ListingSnapshotDto s) {
        return new SnapshotResponse()
            .id(s.id())
            .scrapedAt(s.scrapedAt().atOffset(ZoneOffset.UTC))
            .askingPrice(s.askingPrice())
            .livingAreaM2(s.livingAreaM2())
            .rooms(s.rooms())
            .energyLabel(s.energyLabel())
            .listedOnFundaSince(s.listedOnFundaSince())
            .status(s.status() != null
                ? SnapshotResponse.StatusEnum.valueOf(s.status().name()) : null);
    }

    private ListingReportResponse toReportResponse(ListingReport r) {
        return new ListingReportResponse()
            .listingId(r.listingId())
            .daysListedOnFunda(r.daysListedOnFunda())
            .daysInHermes(r.daysInHermes())
            .currentPrice(r.currentPrice())
            .initialPrice(r.initialPrice())
            .priceChangePct(r.priceChangePct())
            .priceHistory(r.priceHistory().stream()
                .map(p -> new PricePointResponse()
                    .scrapedAt(p.scrapedAt().atOffset(ZoneOffset.UTC))
                    .askingPrice(p.askingPrice()))
                .toList())
            .statusHistory(r.statusHistory().stream()
                .map(s -> new StatusPointResponse()
                    .scrapedAt(s.scrapedAt().atOffset(ZoneOffset.UTC))
                    .status(s.status().name()))
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

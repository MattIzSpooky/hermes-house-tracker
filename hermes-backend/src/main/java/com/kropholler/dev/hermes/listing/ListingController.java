package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.geocoding.ListingGeocodingBackfillService;
import com.kropholler.dev.hermes.listing.openapi.*;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ListingController implements ListingsApi {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;
    private final ListingSummaryService summaryService;
    private final ListingApiMapper listingApiMapper;
    private final RescrapeMapper rescrapeMapper;
    private final ListingGeocodingBackfillService backfillService;

    @Override
    @SuppressWarnings("java:S107") // parameter list is fixed by the generated ListingsApi interface
    public ResponseEntity<ListingPage> getListings(Integer page, Integer size,
            String street, String houseNumber, String houseNumberAddition,
            String zipCode, String city, String province,
            Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String energyLabel,
            Integer radiusKm) {
        ListingSearchParams params = new ListingSearchParams(street, houseNumber, houseNumberAddition, zipCode, city, province,
            minBedrooms, minRooms, minLivingAreaM2, energyLabel, radiusKm);
        Page<ListingDto> result = listingService.findAll(params, PageRequest.of(page, size));
        ListingPage response = new ListingPage()
            .content(result.getContent().stream().map(listingApiMapper::toSummaryResponse).toList())
            .totalElements(result.getTotalElements())
            .totalPages(result.getTotalPages())
            .page(result.getNumber())
            .size(result.getSize());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ListingDetailResponse> getListing(UUID id) {
        return listingService.findById(id)
            .map(dto -> ResponseEntity.ok(listingApiMapper.toDetailResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScrapingSessionResponse> rescrapeListing(UUID id) {
        ListingDto listing = listingService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
        ScrapingSessionDto session = queueService.enqueueRescrape(listing.url(), listing.city());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(rescrapeMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<AiSummaryResponse> getListingSummary(UUID id) {
        return summaryService.findByListingId(id)
            .map(dto -> ResponseEntity.ok(new AiSummaryResponse()
                .listingId(dto.listingId())
                .summary(dto.summary())
                .generatedAt(dto.generatedAt().atOffset(ZoneOffset.UTC))))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No summary available for listing " + id));
    }

    @Override
    public ResponseEntity<Void> requestListingSummaryGeneration(UUID id) {
        listingService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
        summaryService.requestGeneration(id);
        return ResponseEntity.accepted().build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GeocodingBackfillResponse> backfillListingGeocoding() {
        int queuedCount = backfillService.queueMissingGeocoding();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new GeocodingBackfillResponse().queuedCount(queuedCount));
    }
}

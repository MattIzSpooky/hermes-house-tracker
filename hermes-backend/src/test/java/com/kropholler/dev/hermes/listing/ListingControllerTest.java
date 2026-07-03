package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.openapi.ListingDetailResponse;
import com.kropholler.dev.hermes.listing.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingController.class)
@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})
class ListingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ListingService listingService;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ListingSummaryService summaryService;
    @MockitoBean ListingApiMapper listingApiMapper;
    @MockitoBean RescrapeMapper rescrapeMapper;
    @MockitoBean com.kropholler.dev.hermes.listing.geocoding.ListingGeocodingBackfillService backfillService;

    private ListingDto minimalDto(UUID id) {
        return new ListingDto(id, "funda-1", "https://funda.nl/1",
            "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht",
            Instant.now(), Instant.now(), 300000, ListingStatus.FOR_SALE,
            null, 80, 4, 2, "A", null, null);
    }

    @Test
    void getListing_returnsOkWithMappedDetail() throws Exception {
        UUID id = UUID.randomUUID();
        ListingDto dto = minimalDto(id);
        ListingDetailResponse response = new ListingDetailResponse();
        response.setId(id);
        response.setStreet("Dorpstraat");

        when(listingService.findById(id)).thenReturn(Optional.of(dto));
        when(listingApiMapper.toDetailResponse(dto)).thenReturn(response);

        mockMvc.perform(get("/api/listings/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.street").value("Dorpstraat"));
    }

    @Test
    void getListing_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void rescrapeListing_returns202WithSession() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ListingDto dto = minimalDto(id);
        ScrapingSessionDto sessionDto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.PENDING, ScrapingSessionType.RESCRAPE, Instant.now(), null);

        ScrapingSessionResponse sessionResponse = new ScrapingSessionResponse();
        sessionResponse.setId(sessionId);
        sessionResponse.setStatus(ScrapingSessionResponse.StatusEnum.PENDING);

        when(listingService.findById(id)).thenReturn(Optional.of(dto));
        when(queueService.enqueueRescrape(any(), any())).thenReturn(sessionDto);
        when(rescrapeMapper.toResponse(sessionDto)).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/listings/{id}/rescrape", id))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(sessionId.toString()));
    }

    @Test
    void rescrapeListing_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/listings/{id}/rescrape", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void getListingSummary_returnsOkWhenSummaryExists() throws Exception {
        UUID id = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-05-01T10:00:00Z");
        ListingSummaryDto summaryDto = new ListingSummaryDto(id, "A great house.", generatedAt);

        when(summaryService.findByListingId(id)).thenReturn(Optional.of(summaryDto));

        mockMvc.perform(get("/api/listings/{id}/summary", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingId").value(id.toString()))
            .andExpect(jsonPath("$.summary").value("A great house."));
    }

    @Test
    void getListingSummary_returns404WhenNoSummary() throws Exception {
        UUID id = UUID.randomUUID();
        when(summaryService.findByListingId(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/{id}/summary", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void requestListingSummaryGeneration_returns202WhenListingExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.of(minimalDto(id)));

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id))
            .andExpect(status().isAccepted());

        verify(summaryService).requestGeneration(id);
    }

    @Test
    void requestListingSummaryGeneration_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void backfillListingGeocoding_returns202WithQueuedCount() throws Exception {
        when(backfillService.queueMissingGeocoding()).thenReturn(7);

        mockMvc.perform(post("/api/listings/geocoding/backfill"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queuedCount").value(7));
    }
}

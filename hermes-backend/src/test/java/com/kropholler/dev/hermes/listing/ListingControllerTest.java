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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
class ListingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
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

        when(listingService.findById(id)).thenReturn(dto);
        when(listingApiMapper.toDetailResponse(dto)).thenReturn(response);

        mockMvc.perform(get("/api/listings/{id}", id).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.street").value("Dorpstraat"));
    }

    @Test
    void getListing_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id))
            .thenThrow(new com.kropholler.dev.hermes.exception.NotFoundException("Listing " + id + " not found"));

        mockMvc.perform(get("/api/listings/{id}", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void rescrapeListing_asAdmin_returns202WithSession() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ListingDto dto = minimalDto(id);
        ScrapingSessionDto sessionDto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.PENDING, ScrapingSessionType.RESCRAPE, Instant.now(), null);

        ScrapingSessionResponse sessionResponse = new ScrapingSessionResponse();
        sessionResponse.setId(sessionId);
        sessionResponse.setStatus(ScrapingSessionResponse.StatusEnum.PENDING);

        when(listingService.findById(id)).thenReturn(dto);
        when(queueService.enqueueRescrape(any(), any())).thenReturn(sessionDto);
        when(rescrapeMapper.toResponse(sessionDto)).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(sessionId.toString()));
    }

    @Test
    void rescrapeListing_asAdmin_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id))
            .thenThrow(new com.kropholler.dev.hermes.exception.NotFoundException("Listing " + id + " not found"));

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void rescrapeListing_asUser_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void getListingSummary_returnsOkWhenSummaryExists() throws Exception {
        UUID id = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-05-01T10:00:00Z");
        ListingSummaryDto summaryDto = new ListingSummaryDto(id, "A great house.", generatedAt);

        when(summaryService.findByListingId(id)).thenReturn(summaryDto);

        mockMvc.perform(get("/api/listings/{id}/summary", id).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingId").value(id.toString()))
            .andExpect(jsonPath("$.summary").value("A great house."));
    }

    @Test
    void getListingSummary_returns404WhenNoSummary() throws Exception {
        UUID id = UUID.randomUUID();
        when(summaryService.findByListingId(id))
            .thenThrow(new com.kropholler.dev.hermes.exception.NotFoundException("No summary available for listing " + id));

        mockMvc.perform(get("/api/listings/{id}/summary", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void requestListingSummaryGeneration_returns202WhenListingExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(minimalDto(id));

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id).with(jwt()))
            .andExpect(status().isAccepted());

        verify(summaryService).requestGeneration(id);
    }

    @Test
    void requestListingSummaryGeneration_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id))
            .thenThrow(new com.kropholler.dev.hermes.exception.NotFoundException("Listing " + id + " not found"));

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void backfillListingGeocoding_asAdmin_returns202WithQueuedCount() throws Exception {
        when(backfillService.queueMissingGeocoding()).thenReturn(7);

        mockMvc.perform(post("/api/listings/geocoding/backfill")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queuedCount").value(7));
    }

    @Test
    void backfillListingGeocoding_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/listings/geocoding/backfill")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }
}

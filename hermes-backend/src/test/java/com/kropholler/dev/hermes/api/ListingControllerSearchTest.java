package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.listing.ListingSearchParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.report.ReportService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListingController.class)
class ListingControllerSearchTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ListingService listingService;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ReportService reportService;
    @MockitoBean ListingSummaryService summaryService;

    @Test
    void getListings_passesStreetParamToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings").param("street", "Dorpstraat"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Dorpstraat");
    }

    @Test
    void getListings_withNoParams_passesEmptyParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().isEmpty()).isTrue();
    }

    @Test
    void getListings_passesAllSearchParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings")
                .param("street", "Kerkstraat")
                .param("houseNumber", "5")
                .param("zipCode", "1234AB")
                .param("province", "Noord-Holland"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Kerkstraat");
        assertThat(cap.getValue().houseNumber()).isEqualTo("5");
        assertThat(cap.getValue().zipCode()).isEqualTo("1234AB");
        assertThat(cap.getValue().province()).isEqualTo("Noord-Holland");
    }
}

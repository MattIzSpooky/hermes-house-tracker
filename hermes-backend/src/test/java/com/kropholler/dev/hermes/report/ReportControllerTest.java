package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.report.openapi.ListingReportResponse;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ReportService reportService;
    @MockitoBean ReportApiMapper reportApiMapper;

    @Test
    void getListingReport_returnsOkWithMappedBody() throws Exception {
        UUID id = UUID.randomUUID();
        ListingReport report = new ListingReport(id, 30L, 350000, 375000, -6.7, List.of(), ListingStatus.FOR_SALE);

        ListingReportResponse response = new ListingReportResponse();
        response.setListingId(id);
        response.setDaysInHermes(30L);
        response.setCurrentPrice(350000);
        response.setInitialPrice(375000);
        response.setPriceChangePct(-6.7);
        response.setCurrentStatus("FOR_SALE");

        when(reportService.generateReport(id)).thenReturn(Optional.of(report));
        when(reportApiMapper.toReportResponse(report)).thenReturn(response);

        mockMvc.perform(get("/api/listings/{id}/report", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingId").value(id.toString()))
            .andExpect(jsonPath("$.daysInHermes").value(30))
            .andExpect(jsonPath("$.currentPrice").value(350000))
            .andExpect(jsonPath("$.priceChangePct").value(-6.7))
            .andExpect(jsonPath("$.currentStatus").value("FOR_SALE"));
    }

    @Test
    void getListingReport_returnsNotFoundWhenAbsent() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.generateReport(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/{id}/report", id))
            .andExpect(status().isNotFound());
    }
}

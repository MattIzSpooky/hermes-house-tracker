package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.security.NoOpUserProfileSyncFilterTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScrapingSessionController.class)
@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})
class ScrapingSessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ScrapingSessionApiMapper scrapingSessionApiMapper;

    @Test
    void createScrapingSession_asAdmin_returns201WithBody() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T08:00:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.PENDING, ScrapingSessionType.SEARCH, createdAt, null);

        ScrapingSessionResponse response = new ScrapingSessionResponse();
        response.setId(sessionId);
        response.setStatus(ScrapingSessionResponse.StatusEnum.PENDING);
        response.setCreatedAt(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));

        when(queueService.enqueueSearch(any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), anyInt())).thenReturn(dto);
        when(scrapingSessionApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/scraping-sessions")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Amsterdam\",\"pageLimit\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createScrapingSession_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/scraping-sessions")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Amsterdam\",\"pageLimit\":5}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getScrapingSession_asAdmin_returnsOkWhenFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T08:00:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.COMPLETED, ScrapingSessionType.SEARCH, createdAt, createdAt.plusSeconds(60));

        ScrapingSessionResponse response = new ScrapingSessionResponse();
        response.setId(sessionId);
        response.setStatus(ScrapingSessionResponse.StatusEnum.COMPLETED);

        when(queueService.findById(sessionId)).thenReturn(Optional.of(dto));
        when(scrapingSessionApiMapper.toResponse(dto)).thenReturn(response);

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getScrapingSession_asAdmin_returns404WhenNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(queueService.findById(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void getScrapingSession_asUser_returns403() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }
}

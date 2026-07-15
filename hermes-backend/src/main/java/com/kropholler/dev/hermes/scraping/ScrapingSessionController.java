package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.openapi.CreateScrapingSessionRequest;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScrapingSessionController implements ScrapingSessionsApi {

    private final ScrapingQueueService queueService;
    private final ScrapingSessionApiMapper scrapingSessionApiMapper;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScrapingSessionResponse> createScrapingSession(
            CreateScrapingSessionRequest request) {
        ScrapingSessionDto dto = queueService.enqueueSearch(
            request.getCity(),
            request.getMinPrice(),
            request.getMaxPrice(),
            request.getMinArea(),
            request.getMaxArea(),
            request.getPageLimit()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(scrapingSessionApiMapper.toResponse(dto));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScrapingSessionResponse> getScrapingSession(UUID id) {
        ScrapingSessionDto dto = queueService.findById(id);
        return ResponseEntity.ok(scrapingSessionApiMapper.toResponse(dto));
    }

}

package com.kropholler.dev.hermes.scraping;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScrapingSessionController implements ScrapingSessionsApi {

    private final ScrapingQueueService queueService;
    private final ScrapingSessionApiMapper scrapingSessionApiMapper;

    @Override
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
    public ResponseEntity<ScrapingSessionResponse> getScrapingSession(UUID id) {
        return queueService.findById(id)
            .map(dto -> ResponseEntity.ok(scrapingSessionApiMapper.toResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Scraping session " + id + " not found"));
    }

}

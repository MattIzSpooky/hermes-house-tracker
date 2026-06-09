package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.ScrapingSessionsApi;
import com.kropholler.dev.hermes.api.generated.model.CreateScrapingSessionRequest;
import com.kropholler.dev.hermes.api.generated.model.ScrapingSessionResponse;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class ScrapingSessionController implements ScrapingSessionsApi {

    private final ScrapingQueueService queueService;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(dto));
    }

    @Override
    public ResponseEntity<ScrapingSessionResponse> getScrapingSession(UUID id) {
        return queueService.findById(id)
            .map(dto -> ResponseEntity.ok(toResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Scraping session " + id + " not found"));
    }

    private ScrapingSessionResponse toResponse(ScrapingSessionDto dto) {
        return new ScrapingSessionResponse()
            .id(dto.id())
            .status(ScrapingSessionResponse.StatusEnum.valueOf(dto.status().name()))
            .type(ScrapingSessionResponse.TypeEnum.valueOf(dto.type().name()))
            .createdAt(dto.createdAt() != null ? dto.createdAt().atOffset(ZoneOffset.UTC) : null)
            .completedAt(dto.completedAt() != null ? dto.completedAt().atOffset(ZoneOffset.UTC) : null);
    }
}

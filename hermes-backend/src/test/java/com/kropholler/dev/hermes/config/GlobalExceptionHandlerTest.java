package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResponseStatus_returnsProblemDetailWithGivenStatus() {
        ResponseStatusException ex = new ResponseStatusException(NOT_FOUND, "Item not found");

        ResponseEntity<ProblemDetail> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Item not found");
    }

    @Test
    void handleResponseStatus_nullReason_fallsBackToStatusCodeString() {
        ResponseStatusException ex = new ResponseStatusException(NOT_FOUND);

        ResponseEntity<ProblemDetail> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getTitle()).isNotBlank();
    }

    @Test
    void handleGeneral_returns500WithProblemDetail() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ProblemDetail> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTitle()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getDetail()).isEqualTo("Something went wrong");
    }
}

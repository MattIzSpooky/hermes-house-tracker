package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.model.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void handleResponseStatus_includesCorrelationIdFromMdc() {
        MDC.put("correlationId", "err-corr");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(ex);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("err-corr");
    }

    @Test
    void handleResponseStatus_correlationIdIsNullWhenMdcEmpty() {
        MDC.clear();
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(ex);

        assertThat(response.getBody().getCorrelationId()).isNull();
    }

    @Test
    void handleGeneral_includesCorrelationIdFromMdc() {
        MDC.put("correlationId", "gen-corr");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(new RuntimeException("oops"));

        assertThat(response.getBody().getCorrelationId()).isEqualTo("gen-corr");
    }
}

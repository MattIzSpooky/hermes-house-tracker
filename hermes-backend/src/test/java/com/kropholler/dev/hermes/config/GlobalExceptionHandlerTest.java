package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.exception.ForbiddenException;
import com.kropholler.dev.hermes.exception.NotFoundException;
import com.kropholler.dev.hermes.exception.UnprocessableEntityException;
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
    void handleNotFound_returns404WithProblemDetail() {
        NotFoundException ex = new NotFoundException("Agent task not found");

        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getTitle()).isEqualTo("Agent task not found");
        assertThat(response.getBody().getDetail()).isEqualTo("Agent task not found");
    }

    @Test
    void handleUnprocessableEntity_returns422WithProblemDetail() {
        UnprocessableEntityException ex = new UnprocessableEntityException("Address could not be geocoded");

        ResponseEntity<ProblemDetail> response = handler.handleUnprocessableEntity(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().getTitle()).isEqualTo("Address could not be geocoded");
    }

    @Test
    void handleForbidden_returns403WithProblemDetail() {
        ForbiddenException ex = new ForbiddenException("Not authorized to access this agent task");

        ResponseEntity<ProblemDetail> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getTitle()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getDetail()).isEqualTo("Not authorized to access this agent task");
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

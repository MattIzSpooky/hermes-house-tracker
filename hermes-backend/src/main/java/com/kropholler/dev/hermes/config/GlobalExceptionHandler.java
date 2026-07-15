package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.exception.ForbiddenException;
import com.kropholler.dev.hermes.exception.NotFoundException;
import com.kropholler.dev.hermes.exception.UnprocessableEntityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail body = ProblemDetail.forStatus(ex.getStatusCode());
        body.setTitle(ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString());
        body.setDetail(ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problemResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    ResponseEntity<ProblemDetail> handleUnprocessableEntity(UnprocessableEntityException ex) {
        return problemResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // Mirrors ProblemDetailAccessDeniedHandler's body shape so a domain-level authorization
    // failure looks identical to one Spring Security's filter chain produces (e.g. @PreAuthorize).
    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        body.setTitle("FORBIDDEN");
        body.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, String message) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setTitle(message != null ? message : status.toString());
        body.setDetail(message);
        return ResponseEntity.status(status).body(body);
    }

    // Let Spring Security's ExceptionTranslationFilter/AccessDeniedHandler handle authorization
    // failures (including @PreAuthorize's AuthorizationDeniedException, a subtype of this). If this
    // advice's catch-all Exception handler below caught it first, it would always report 500 instead
    // of the 403 the security filter chain is configured to produce.
    @ExceptionHandler(AccessDeniedException.class)
    void rethrowAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        body.setTitle("INTERNAL_SERVER_ERROR");
        body.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void usesProvidedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "my-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("correlationId")));

        assertThat(capturedMdc.get()).isEqualTo("my-id");
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("my-id");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("correlationId")));

        assertThat(capturedMdc.get()).isNotNull().isNotEmpty();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(capturedMdc.get());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void clearsMdcEvenOnFilterChainException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "ex-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {}

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesCorrelationIdForBlankHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("correlationId")));

        assertThat(capturedMdc.get()).isNotBlank();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(capturedMdc.get());
    }
}

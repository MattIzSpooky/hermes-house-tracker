package com.kropholler.dev.hermes.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final String ALREADY_FILTERED = CorrelationIdFilter.class.getName() + ".FILTERED";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getAttribute(ALREADY_FILTERED) != null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(ALREADY_FILTERED, Boolean.TRUE);

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String id = httpRequest.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        } else {
            id = id.replaceAll("[\r\n]", "");
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
        }
        MDC.put(MDC_KEY, id);
        httpResponse.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

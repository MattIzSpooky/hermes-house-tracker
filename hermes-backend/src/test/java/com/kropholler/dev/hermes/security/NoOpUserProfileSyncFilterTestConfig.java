package com.kropholler.dev.hermes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Supplies the {@code userProfileSyncFilter}-qualified {@link OncePerRequestFilter} bean
 * that {@code SecurityConfig.filterChain(...)} requires, for any {@code @WebMvcTest} that
 * imports {@code SecurityConfig} but isn't testing the sync behavior itself (that's covered
 * by {@code UserProfileSyncFilterTest}). A real pass-through filter, not a mock — simpler
 * than stubbing a mock's {@code doFilter} to call through in every test.
 */
@TestConfiguration
public class NoOpUserProfileSyncFilterTestConfig {

    @Bean
    @Qualifier("userProfileSyncFilter")
    OncePerRequestFilter userProfileSyncFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                filterChain.doFilter(request, response);
            }
        };
    }
}

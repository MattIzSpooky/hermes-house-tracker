package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Keeps {@code user_profiles.email} in sync with the JWT's {@code email} claim.
 * Runs on every authenticated request so that background jobs (which have no JWT
 * available) can still look up a user's email via their profile.
 *
 * Lives in the {@code profile} package (not {@code config}, where it's registered
 * from) so its own dependency on {@link UserProfileRepository} stays intra-module —
 * {@code config} only ever references it through the generic, qualified
 * {@link OncePerRequestFilter} type, never this concrete class, to avoid a
 * {@code config -> profile} Spring Modulith edge that would close a cycle with the
 * existing {@code profile -> listing -> scraping -> funda -> config} chain.
 *
 * Deliberately NOT a {@code @Component}: Spring Boot's {@code @WebMvcTest} slices
 * auto-detect any {@code @Component} implementing {@link jakarta.servlet.Filter}
 * regardless of package, which would collide with the test-only no-op replacement
 * every such slice needs. Instead it's registered explicitly via a {@code @Bean}
 * method in {@link UserProfileSyncFilterConfig}, which plain component scanning
 * (including {@code @WebMvcTest}'s) does not treat as web-layer-relevant and so
 * never auto-includes.
 */
@RequiredArgsConstructor
class UserProfileSyncFilter extends OncePerRequestFilter {

    private final UserProfileRepository userProfileRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            syncEmail(CurrentUser.from(jwt));
        }
        filterChain.doFilter(request, response);
    }

    private void syncEmail(CurrentUser user) {
        if (user.email() == null) return;
        UserProfileEntity entity = userProfileRepository.findById(user.id()).orElseGet(() -> {
            UserProfileEntity e = new UserProfileEntity();
            e.setUserId(user.id());
            e.setUpdatedAt(Instant.now());
            return e;
        });
        if (user.email().equals(entity.getEmail())) return;
        entity.setEmail(user.email());
        userProfileRepository.save(entity);
    }
}

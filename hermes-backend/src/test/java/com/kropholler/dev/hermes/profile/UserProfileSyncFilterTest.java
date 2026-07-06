package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileSyncFilterTest {

    @Mock UserProfileRepository userProfileRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    private UserProfileSyncFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserProfileSyncFilter(userProfileRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwtWithEmail(UUID subject, String email) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("email", email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }

    @Test
    void doFilterInternal_newProfile_createsRowWithEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "user@hermes.local")));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@hermes.local");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_existingProfileWithChangedEmail_updatesEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "new@hermes.local")));
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setEmail("old@hermes.local");
        existing.setUpdatedAt(Instant.now().minusSeconds(3600));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@hermes.local");
    }

    @Test
    void doFilterInternal_existingProfileWithSameEmail_doesNotSave() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "same@hermes.local")));
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setEmail("same@hermes.local");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).save(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noAuthentication_skipsSyncAndContinuesChain() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).save(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_jwtWithoutEmailClaim_skipsSync() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt jwtNoEmail = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtNoEmail));

        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }
}

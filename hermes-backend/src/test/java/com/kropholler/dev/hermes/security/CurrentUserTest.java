package com.kropholler.dev.hermes.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @Test
    void from_extractsIdUsernameAndRoles() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.id()).isEqualTo(subject);
        assertThat(currentUser.username()).isEqualTo("testuser");
        assertThat(currentUser.roles()).containsExactly("user");
    }

    @Test
    void from_missingRealmAccess_returnsEmptyRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.roles()).isEmpty();
    }
}

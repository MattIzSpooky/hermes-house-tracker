package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.favorites.FavoriteApiMapper;
import com.kropholler.dev.hermes.favorites.FavoriteController;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean FavoriteService favoriteService;
    @MockitoBean FavoriteApiMapper favoriteApiMapper;

    @Test
    void unauthenticatedRequest_isRejectedWith401() throws Exception {
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(get("/api/favorites/{clientId}", clientId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_isAllowedThrough() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(favoriteService.findByClientId(clientId)).thenReturn(List.of());

        mockMvc.perform(get("/api/favorites/{clientId}", clientId).with(jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void jwtAuthenticationConverter_mapsRealmRolesToAuthorities() {
        SecurityConfig config = new SecurityConfig();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("realm_access", Map.of("roles", List.of("admin", "user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        var authentication = config.jwtAuthenticationConverter().convert(jwt);

        // Spring Security 7's JwtAuthenticationConverter unconditionally adds a
        // FACTOR_BEARER authentication-factor authority alongside whatever the
        // jwtGrantedAuthoritiesConverter returns, so assert containment rather
        // than an exact set.
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .contains("ROLE_ADMIN", "ROLE_USER");
    }
}

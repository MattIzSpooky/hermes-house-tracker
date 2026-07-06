package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.security.NoOpUserProfileSyncFilterTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})
class FavoriteControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean
    FavoriteService favoriteService;
    @MockitoBean
    FavoriteApiMapper favoriteApiMapper;

    @Test
    void getFavorites_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-01-15T10:00:00Z");

        FavoriteDto dto = new FavoriteDto(listingId, savedAt);
        FavoriteResponse response = new FavoriteResponse();
        response.setListingId(listingId);
        response.setSavedAt(OffsetDateTime.ofInstant(savedAt, ZoneOffset.UTC));

        when(favoriteService.findByUserId(userId)).thenReturn(List.of(dto));
        when(favoriteApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/favorites")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].listingId").value(listingId.toString()))
            .andExpect(jsonPath("$[0].savedAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void addFavorite_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(put("/api/favorites/{listingId}", listingId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(favoriteService).addFavorite(eq(userId), eq(listingId));
    }

    @Test
    void removeFavorite_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/favorites/{listingId}", listingId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(favoriteService).removeFavorite(eq(userId), eq(listingId));
    }
}

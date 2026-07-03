package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})
class FavoriteControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean
    FavoriteService favoriteService;
    @MockitoBean
    FavoriteApiMapper favoriteApiMapper;

    @Test
    void getFavorites_returnsMappedList() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-01-15T10:00:00Z");

        FavoriteDto dto = new FavoriteDto(listingId, savedAt);
        FavoriteResponse response = new FavoriteResponse();
        response.setListingId(listingId);
        response.setSavedAt(OffsetDateTime.ofInstant(savedAt, ZoneOffset.UTC));

        when(favoriteService.findByClientId(clientId)).thenReturn(List.of(dto));
        when(favoriteApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/favorites/{clientId}", clientId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].listingId").value(listingId.toString()))
            .andExpect(jsonPath("$[0].savedAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void addFavorite_returns204() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(put("/api/favorites/{clientId}/{listingId}", clientId, listingId))
            .andExpect(status().isNoContent());

        verify(favoriteService).addFavorite(clientId, listingId);
    }

    @Test
    void removeFavorite_returns204() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/favorites/{clientId}/{listingId}", clientId, listingId))
            .andExpect(status().isNoContent());

        verify(favoriteService).removeFavorite(clientId, listingId);
    }
}

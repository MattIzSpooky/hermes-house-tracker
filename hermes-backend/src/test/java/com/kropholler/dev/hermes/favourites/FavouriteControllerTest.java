package com.kropholler.dev.hermes.favourites;

import com.kropholler.dev.hermes.favourites.openapi.FavouriteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(FavouriteController.class)
class FavouriteControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FavouriteService favouriteService;
    @MockitoBean FavouriteApiMapper favouriteApiMapper;

    @Test
    void getFavourites_returnsMappedList() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-01-15T10:00:00Z");

        FavouriteDto dto = new FavouriteDto(listingId, savedAt);
        FavouriteResponse response = new FavouriteResponse();
        response.setListingId(listingId);
        response.setSavedAt(OffsetDateTime.ofInstant(savedAt, ZoneOffset.UTC));

        when(favouriteService.findByClientId(clientId)).thenReturn(List.of(dto));
        when(favouriteApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/favourites/{clientId}", clientId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].listingId").value(listingId.toString()))
            .andExpect(jsonPath("$[0].savedAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void addFavourite_returns204() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(put("/api/favourites/{clientId}/{listingId}", clientId, listingId))
            .andExpect(status().isNoContent());

        verify(favouriteService).addFavourite(clientId, listingId);
    }

    @Test
    void removeFavourite_returns204() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/favourites/{clientId}/{listingId}", clientId, listingId))
            .andExpect(status().isNoContent());

        verify(favouriteService).removeFavourite(clientId, listingId);
    }
}

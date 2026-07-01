package com.kropholler.dev.hermes.favorites;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    FavoriteRepository repository;
    @InjectMocks
    FavoriteService service;

    @Test
    void findByClientId_returnsMappedDtos() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        FavoriteEntity favourite = new FavoriteEntity();
        favourite.setClientId(clientId);
        favourite.setListingId(listingId);
        favourite.setSavedAt(now);

        when(repository.findByClientId(clientId)).thenReturn(List.of(favourite));

        List<FavoriteDto> result = service.findByClientId(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().listingId()).isEqualTo(listingId);
        assertThat(result.getFirst().savedAt()).isEqualTo(now);
    }

    @Test
    void addFavorite_savesWhenNotAlreadyPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(false);

        service.addFavorite(clientId, listingId);

        ArgumentCaptor<FavoriteEntity> cap = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getClientId()).isEqualTo(clientId);
        assertThat(cap.getValue().getListingId()).isEqualTo(listingId);
    }

    @Test
    void addFavorite_doesNotSaveWhenAlreadyPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(true);

        service.addFavorite(clientId, listingId);

        verify(repository, never()).save(any());
    }

    @Test
    void removeFavorite_delegatesToRepository() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        service.removeFavorite(clientId, listingId);

        verify(repository).deleteByClientIdAndListingId(clientId, listingId);
    }

    @Test
    void isFavorite_returnsTrueWhenPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(true);

        assertThat(service.isFavorite(clientId, listingId)).isTrue();
    }

    @Test
    void isFavorite_returnsFalseWhenAbsent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(false);

        assertThat(service.isFavorite(clientId, listingId)).isFalse();
    }
}

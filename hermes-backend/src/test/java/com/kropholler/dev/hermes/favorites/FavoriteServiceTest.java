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
    void findByUserId_returnsMappedDtos() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        FavoriteEntity favourite = new FavoriteEntity();
        favourite.setUserId(userId);
        favourite.setListingId(listingId);
        favourite.setSavedAt(now);

        when(repository.findByUserId(userId)).thenReturn(List.of(favourite));

        List<FavoriteDto> result = service.findByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().listingId()).isEqualTo(listingId);
        assertThat(result.getFirst().savedAt()).isEqualTo(now);
    }

    @Test
    void addFavorite_savesWhenNotAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(false);

        service.addFavorite(userId, listingId);

        ArgumentCaptor<FavoriteEntity> cap = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        assertThat(cap.getValue().getListingId()).isEqualTo(listingId);
    }

    @Test
    void addFavorite_doesNotSaveWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(true);

        service.addFavorite(userId, listingId);

        verify(repository, never()).save(any());
    }

    @Test
    void removeFavorite_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        service.removeFavorite(userId, listingId);

        verify(repository).deleteByUserIdAndListingId(userId, listingId);
    }

    @Test
    void isFavorite_returnsTrueWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(true);

        assertThat(service.isFavorite(userId, listingId)).isTrue();
    }

    @Test
    void isFavorite_returnsFalseWhenAbsent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(false);

        assertThat(service.isFavorite(userId, listingId)).isFalse();
    }
}

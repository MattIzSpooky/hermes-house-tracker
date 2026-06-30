package com.kropholler.dev.hermes.favourites;

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
class FavouriteServiceTest {

    @Mock FavouriteRepository repository;
    @InjectMocks FavouriteService service;

    @Test
    void findByClientId_returnsMappedDtos() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        Favourite favourite = new Favourite();
        favourite.setClientId(clientId);
        favourite.setListingId(listingId);
        favourite.setSavedAt(now);

        when(repository.findByClientId(clientId)).thenReturn(List.of(favourite));

        List<FavouriteDto> result = service.findByClientId(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().listingId()).isEqualTo(listingId);
        assertThat(result.getFirst().savedAt()).isEqualTo(now);
    }

    @Test
    void addFavourite_savesWhenNotAlreadyPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(false);

        service.addFavourite(clientId, listingId);

        ArgumentCaptor<Favourite> cap = ArgumentCaptor.forClass(Favourite.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getClientId()).isEqualTo(clientId);
        assertThat(cap.getValue().getListingId()).isEqualTo(listingId);
    }

    @Test
    void addFavourite_doesNotSaveWhenAlreadyPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(true);

        service.addFavourite(clientId, listingId);

        verify(repository, never()).save(any());
    }

    @Test
    void removeFavourite_delegatesToRepository() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        service.removeFavourite(clientId, listingId);

        verify(repository).deleteByClientIdAndListingId(clientId, listingId);
    }

    @Test
    void isFavourite_returnsTrueWhenPresent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(true);

        assertThat(service.isFavourite(clientId, listingId)).isTrue();
    }

    @Test
    void isFavourite_returnsFalseWhenAbsent() {
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByClientIdAndListingId(clientId, listingId)).thenReturn(false);

        assertThat(service.isFavourite(clientId, listingId)).isFalse();
    }
}

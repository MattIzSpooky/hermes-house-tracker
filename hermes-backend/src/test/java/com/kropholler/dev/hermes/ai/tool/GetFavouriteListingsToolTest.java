package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.favorites.FavoriteDto;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFavouriteListingsToolTest {

    @Mock
    FavoriteService favoriteService;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper mapper;

    private final UUID clientId = UUID.randomUUID();

    private ListingDto dto(UUID id) {
        return new ListingDto(id, "f", "u", "Straat", "5", null, "1234AB",
            "Rotterdam", "Zuid-Holland", Instant.now(), Instant.now(),
            400000, ListingStatus.FOR_SALE, null, 100, 5, 3, "A", null, null);
    }

    private GetFavouriteListingsTool tool(AtomicReference<List<ChatListingCard>> holder) {
        return new GetFavouriteListingsTool(clientId, favoriteService, listingService, mapper, holder, new SimpleMeterRegistry());
    }

    @Test
    void getFavouriteListings_noFavourites_returnsNoneSavedMessage() {
        when(favoriteService.findByClientId(clientId)).thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).getFavouriteListings();

        assertThat(result).contains("no saved listings");
    }

    @Test
    void getFavouriteListings_withFavourites_populatesHolder() {
        UUID listingId = UUID.randomUUID();
        FavoriteDto fav = new FavoriteDto(listingId, Instant.now());
        ListingDto listingDto = dto(listingId);
        ChatListingCard card = new ChatListingCard(listingId, "Straat", "5", null, "Rotterdam", "Zuid-Holland", 400000, 3, 100, "A", "FOR_SALE");

        when(favoriteService.findByClientId(clientId)).thenReturn(List.of(fav));
        when(listingService.findById(listingId)).thenReturn(Optional.of(listingDto));
        when(mapper.toChatListingCard(listingDto)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).getFavouriteListings();

        assertThat(holder.get()).hasSize(1);
        assertThat(result).contains("1 saved listing(s)");
        assertThat(result).contains("Rotterdam");
    }

    @Test
    void getFavouriteListings_listingDeleted_returnsCouldNotBeFoundMessage() {
        UUID listingId = UUID.randomUUID();
        FavoriteDto fav = new FavoriteDto(listingId, Instant.now());

        when(favoriteService.findByClientId(clientId)).thenReturn(List.of(fav));
        when(listingService.findById(listingId)).thenReturn(Optional.empty());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).getFavouriteListings();

        assertThat(result).contains("could not be found");
    }

    @Test
    void getFavouriteListings_cardWithAdditionAndNullPrice_formatsCorrectly() {
        // Covers L66 true (houseNumberAddition != null → appended) and L68 false (price null → not appended)
        UUID listingId = UUID.randomUUID();
        FavoriteDto fav = new FavoriteDto(listingId, Instant.now());
        ListingDto listingDto = dto(listingId);
        ChatListingCard card = new ChatListingCard(listingId, "Straat", "5", "C", "Rotterdam", "Zuid-Holland", null, 3, 100, "A", "FOR_SALE");

        when(favoriteService.findByClientId(clientId)).thenReturn(List.of(fav));
        when(listingService.findById(listingId)).thenReturn(Optional.of(listingDto));
        when(mapper.toChatListingCard(listingDto)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).getFavouriteListings();

        assertThat(result).contains("Straat 5C");
        assertThat(result).doesNotContain("€");
    }
}

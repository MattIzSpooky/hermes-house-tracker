package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.ai.ChatListingCard;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSearchToolTest {

    @Mock
    ListingService listingService;

    @Mock
    ChatListingCardMapper mapper;

    private static ListingDto dto(UUID id) {
        return new ListingDto(id, "FND001",
                "https://funda.nl/listing/1",
                "Teststraat", "1", null, "1234AB",
                "Amsterdam", "Noord-Holland",
                Instant.now(), Instant.now(),
                450000, ListingStatus.FOR_SALE,
                "Ruim appartement met balkon.", 85, 4,
                3, "A", null);
    }

    @Test
    void searchListings_returnsCardsAndPopulatesHolder() {
        UUID id = UUID.randomUUID();
        ListingDto listing = dto(id);
        ChatListingCard card = new ChatListingCard(id, "Teststraat", "1", null,
                "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE",
                "https://funda.nl/listing/1");

        ListingSearchTool.SearchParams params = new ListingSearchTool.SearchParams(
                null, 500000, 3, null, null, null, "Amsterdam", null);

        when(listingService.findForChat(null, 500000, 3, null, null, null, "Amsterdam", null))
                .thenReturn(List.of(listing));
        when(mapper.toChatListingCard(listing)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder);

        List<ChatListingCard> result = tool.searchListings(params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).city()).isEqualTo("Amsterdam");
        assertThat(holder.get()).hasSize(1);
    }

    @Test
    void searchListings_emptyResults_holderRemainsEmpty() {
        ListingSearchTool.SearchParams params = new ListingSearchTool.SearchParams(
                null, null, null, null, null, null, null, "south-facing garden");

        when(listingService.findForChat(null, null, null, null, null, null, null, "south-facing garden"))
                .thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder);

        List<ChatListingCard> result = tool.searchListings(params);

        assertThat(result).isEmpty();
        assertThat(holder.get()).isEmpty();
    }
}

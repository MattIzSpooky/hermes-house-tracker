package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.ListingSearchTool;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
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
                3, "A", null, null);
    }

    @Test
    void searchListings_returnsCardsAndPopulatesHolder() {
        UUID id = UUID.randomUUID();
        ListingDto listing = dto(id);
        ChatListingCard card = new ChatListingCard(id, "Teststraat", "1", null,
                "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE");

        when(listingService.findForChat(null, 500000, 3, null, null, null, "Amsterdam", null, false, null, null, null, null))
                .thenReturn(List.of(listing));
        when(mapper.toChatListingCard(listing)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        List<ChatListingCard> result = tool.searchListings("Amsterdam", null, null, 500000, 3, null, null, null, "asc", null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).city()).isEqualTo("Amsterdam");
        assertThat(holder.get()).hasSize(1);
    }

    @Test
    void searchListings_priceSortDesc_passesTrueToService() {
        UUID id = UUID.randomUUID();
        ListingDto listing = dto(id);
        ChatListingCard card = new ChatListingCard(id, "Teststraat", "1", null,
                "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE");

        when(listingService.findForChat(null, null, null, null, null, null, null, null, true, null, null, null, null))
                .thenReturn(List.of(listing));
        when(mapper.toChatListingCard(listing)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        List<ChatListingCard> result = tool.searchListings(null, null, null, null, null, null, null, null, "desc", null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchListings_nullPriceSort_defaultsToAscending() {
        when(listingService.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, null))
                .thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        List<ChatListingCard> result = tool.searchListings(null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void searchListings_emptyResults_holderIsSetToEmpty() {
        when(listingService.findForChat(null, null, null, null, null, null, null, "south-facing garden", false, null, null, null, null))
                .thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        List<ChatListingCard> result = tool.searchListings(null, null, null, null, null, null, null, "south-facing garden", null, null, null, null);

        assertThat(result).isEmpty();
        assertThat(holder.get()).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void searchListings_serviceThrows_exceptionPropagatesAndHolderUnchanged() {
        when(listingService.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, null))
                .thenThrow(new RuntimeException("DB error"));

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        assertThatThrownBy(() -> tool.searchListings(null, null, null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
        assertThat(holder.get()).isEmpty();
    }

    @Test
    void searchListings_blankStringParams_treatedAsNull() {
        // Covers the blankToNull branch: s != null && s.isBlank() → return null
        when(listingService.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, null))
                .thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        List<ChatListingCard> result = tool.searchListings("  ", "  ", null, null, null, null, null, "  ", null, "  ", "  ", null);

        assertThat(result).isEmpty();
    }
}

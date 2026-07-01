package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.json.PriceDropParams;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.PriceDropResult;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindPriceDropToolTest {

    @Mock ListingService listingService;
    @Mock ChatListingCardMapper mapper;

    private ListingDto dto(UUID id) {
        return new ListingDto(id, "f", "u", "Straat", "1", null, "1234AB",
            "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            280000, ListingStatus.FOR_SALE, null, 90, 4, 2, "B", null);
    }

    private FindPriceDropTool tool(AtomicReference<List<ChatListingCard>> holder) {
        return new FindPriceDropTool(listingService, mapper, holder, new SimpleMeterRegistry());
    }

    @Test
    void execute_withResults_populatesHolderAndReturnsFormattedList() {
        UUID id = UUID.randomUUID();
        ListingDto listingDto = dto(id);
        PriceDropResult drop = new PriceDropResult(listingDto, 300000, 280000, 6.67);
        ChatListingCard card = new ChatListingCard(id, "Straat", "1", null, "Utrecht", "Utrecht", 280000, 2, 90, "B", "FOR_SALE");

        when(listingService.findPriceDropListings("Utrecht", 5.0)).thenReturn(List.of(drop));
        when(mapper.toChatListingCard(listingDto)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).execute(new PriceDropParams("Utrecht", 5.0));

        assertThat(holder.get()).hasSize(1);
        assertThat(result).contains("1 listing(s) with price drops");
        assertThat(result).containsPattern("6[.,]7%");
    }

    @Test
    void execute_noResults_returnsEmptyMessage() {
        when(listingService.findPriceDropListings(null, 1.0)).thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).execute(new PriceDropParams(null, null));

        assertThat(result).contains("No listings found");
        assertThat(holder.get()).isEmpty();
    }

    @Test
    void execute_nullMinDrop_defaultsToOnePercent() {
        when(listingService.findPriceDropListings(null, 1.0)).thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        tool(holder).execute(new PriceDropParams(null, null));
    }

    @Test
    void execute_blankCity_treatedAsNull() {
        when(listingService.findPriceDropListings(null, 1.0)).thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        tool(holder).execute(new PriceDropParams("  ", null));
    }

    @Test
    void execute_noResults_withNonNullCity_includesCityInMessage() {
        // Covers L48 true branch: city != null → " in " + city appended
        when(listingService.findPriceDropListings("Amsterdam", 1.0)).thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).execute(new PriceDropParams("Amsterdam", null));

        assertThat(result).contains("No listings found");
        assertThat(result).contains("in Amsterdam");
    }

    @Test
    void execute_withHouseNumberAddition_appendsAdditionToAddress() {
        // Covers L55 true branch: houseNumberAddition != null → appended
        UUID id = UUID.randomUUID();
        ListingDto withAddition = new ListingDto(id, "f", "u", "Straat", "1", "B", "1234AB",
            "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            280000, ListingStatus.FOR_SALE, null, 90, 4, 2, "B", null);
        PriceDropResult drop = new PriceDropResult(withAddition, 300000, 280000, 6.67);
        ChatListingCard card = new ChatListingCard(id, "Straat", "1", "B", "Utrecht", "Utrecht", 280000, 2, 90, "B", "FOR_SALE");

        when(listingService.findPriceDropListings(null, 1.0)).thenReturn(List.of(drop));
        when(mapper.toChatListingCard(withAddition)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        String result = tool(holder).execute(new PriceDropParams(null, null));

        assertThat(result).contains("Straat 1B");
    }
}

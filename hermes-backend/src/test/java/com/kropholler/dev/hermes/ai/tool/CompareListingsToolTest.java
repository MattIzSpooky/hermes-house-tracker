package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.json.AddressEntry;
import com.kropholler.dev.hermes.ai.tool.json.AddressList;
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
class CompareListingsToolTest {

    @Mock ListingService listingService;
    @Mock ChatListingCardMapper mapper;

    private ListingDto dto(UUID id, String street) {
        return new ListingDto(id, "f", "u", street, "1", null, "1234AB",
            "Amsterdam", "Noord-Holland", Instant.now(), Instant.now(),
            300000, ListingStatus.FOR_SALE, null, 80, 4, 2, "B", null);
    }

    private CompareListingsTool tool(AtomicReference<List<ChatListingCard>> holder) {
        return new CompareListingsTool(listingService, mapper, holder, new SimpleMeterRegistry());
    }

    @Test
    void compareListings_allFound_populatesHolderAndReturnsComparison() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ListingDto dto1 = dto(id1, "Dorpstraat");
        ListingDto dto2 = dto(id2, "Kerkstraat");
        ChatListingCard card1 = new ChatListingCard(id1, "Dorpstraat", "1", null, "Amsterdam", "Noord-Holland", 300000, 2, 80, "B", "FOR_SALE");
        ChatListingCard card2 = new ChatListingCard(id2, "Kerkstraat", "1", null, "Amsterdam", "Noord-Holland", 300000, 2, 80, "B", "FOR_SALE");

        when(listingService.findByAddress("Dorpstraat", "1", "Amsterdam")).thenReturn(Optional.of(dto1));
        when(listingService.findByAddress("Kerkstraat", "1", "Amsterdam")).thenReturn(Optional.of(dto2));
        when(mapper.toChatListingCard(dto1)).thenReturn(card1);
        when(mapper.toChatListingCard(dto2)).thenReturn(card2);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AddressList params = new AddressList(List.of(
            new AddressEntry("Dorpstraat", "1", "Amsterdam"),
            new AddressEntry("Kerkstraat", "1", "Amsterdam")
        ));

        String result = tool(holder).compareListings(params);

        assertThat(holder.get()).hasSize(2);
        assertThat(result).contains("Comparison of 2 properties");
        assertThat(result).contains("Dorpstraat");
        assertThat(result).contains("Kerkstraat");
    }

    @Test
    void compareListings_noneFound_returnsNoneFoundMessage() {
        when(listingService.findByAddress("Unknown", "1", "X")).thenReturn(Optional.empty());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AddressList params = new AddressList(List.of(new AddressEntry("Unknown", "1", "X")));

        String result = tool(holder).compareListings(params);

        assertThat(result).contains("None of the requested properties were found in the database");
    }

    @Test
    void compareListings_someNotFound_listsNotFoundInOutput() {
        UUID id = UUID.randomUUID();
        ListingDto dto = dto(id, "Dorpstraat");
        ChatListingCard card = new ChatListingCard(id, "Dorpstraat", "1", null, "Amsterdam", "Noord-Holland", 300000, 2, 80, "B", "FOR_SALE");

        when(listingService.findByAddress("Dorpstraat", "1", "Amsterdam")).thenReturn(Optional.of(dto));
        when(listingService.findByAddress("Missing", "99", "Utrecht")).thenReturn(Optional.empty());
        when(mapper.toChatListingCard(dto)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AddressList params = new AddressList(List.of(
            new AddressEntry("Dorpstraat", "1", "Amsterdam"),
            new AddressEntry("Missing", "99", "Utrecht")
        ));

        String result = tool(holder).compareListings(params);

        assertThat(result).contains("Not found");
        assertThat(result).contains("Missing 99");
        assertThat(holder.get()).hasSize(1);
    }

    @Test
    void compareListings_dtoWithNullableNumericFieldsAndNonNullAddition_formatsCorrectly() {
        UUID id = UUID.randomUUID();
        // houseNumberAddition="A" covers L61 true; plotAreaM2=50 covers L67 true;
        // null price/bedrooms/rooms/livingAreaM2/energyLabel/status cover the "unknown" branches on L63-69
        ListingDto sparse = new ListingDto(id, "f", "u", "Sparsestraat", "3", "A",
            "5678CD", "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            null, null, null, null, null, null, null, 50);
        ChatListingCard card = new ChatListingCard(id, "Sparsestraat", "3", "A", "Utrecht", "Utrecht", null, null, null, null, null);

        when(listingService.findByAddress("Sparsestraat", "3", "Utrecht")).thenReturn(Optional.of(sparse));
        when(mapper.toChatListingCard(sparse)).thenReturn(card);

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        AddressList params = new AddressList(List.of(new AddressEntry("Sparsestraat", "3", "Utrecht")));

        String result = tool(holder).compareListings(params);

        assertThat(result).contains("Sparsestraat 3A");
        assertThat(result).contains("50 m²");
        assertThat(result).contains("unknown");
    }
}

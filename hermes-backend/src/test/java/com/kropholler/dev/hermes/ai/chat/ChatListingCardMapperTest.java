package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatListingCardMapperTest {

    private final ChatListingCardMapper mapper = Mappers.getMapper(ChatListingCardMapper.class);

    private ListingDto dto(ListingStatus status) {
        return new ListingDto(UUID.randomUUID(), "funda-1", "https://funda.nl/1",
            "Hoofdstraat", "12", "B", "2000AB", "Rotterdam", "Zuid-Holland",
            Instant.now(), Instant.now(), 420000, status,
            "Description", 100, 5, 3, "A", 200, null);
    }

    @Test
    void toChatListingCard_mapsStatusToName() {
        ChatListingCard card = mapper.toChatListingCard(dto(ListingStatus.FOR_SALE));

        assertThat(card.status()).isEqualTo("FOR_SALE");
    }

    @Test
    void toChatListingCard_nullStatus_producesNullStatus() {
        ChatListingCard card = mapper.toChatListingCard(dto(null));

        assertThat(card.status()).isNull();
    }

    @Test
    void toChatListingCard_mapsAddressAndPriceFields() {
        ChatListingCard card = mapper.toChatListingCard(dto(ListingStatus.SOLD));

        assertThat(card.street()).isEqualTo("Hoofdstraat");
        assertThat(card.houseNumber()).isEqualTo("12");
        assertThat(card.houseNumberAddition()).isEqualTo("B");
        assertThat(card.city()).isEqualTo("Rotterdam");
        assertThat(card.currentPrice()).isEqualTo(420000);
        assertThat(card.bedrooms()).isEqualTo(3);
        assertThat(card.livingAreaM2()).isEqualTo(100);
    }
}

package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListingMapperTest {

    private final ListingMapper mapper = Mappers.getMapper(ListingMapper.class);

    @Test
    void toDto_injectsCurrentPrice() {
        Listing listing = new Listing();
        listing.setStreet("Kerkstraat");
        listing.setHouseNumber("5");
        listing.setCity("Amsterdam");
        listing.setStatus(ListingStatus.FOR_SALE);
        listing.setFirstSeenAt(Instant.now());
        listing.setLastSeenAt(Instant.now());

        ListingDto dto = mapper.toDto(listing, 275000);

        assertThat(dto.currentPrice()).isEqualTo(275000);
        assertThat(dto.street()).isEqualTo("Kerkstraat");
        assertThat(dto.city()).isEqualTo("Amsterdam");
    }

    @Test
    void toDto_nullCurrentPrice_producesNullPrice() {
        Listing listing = new Listing();
        listing.setFirstSeenAt(Instant.now());
        listing.setLastSeenAt(Instant.now());

        ListingDto dto = mapper.toDto(listing, null);

        assertThat(dto.currentPrice()).isNull();
    }

    @Test
    void toDto_priceHistoryEntry_mapsFields() {
        PriceHistoryEntry entry = new PriceHistoryEntry();
        entry.setId(UUID.randomUUID());
        entry.setListingId(UUID.randomUUID());
        entry.setPrice(300000);
        entry.setStatus("asking_price");
        entry.setTimestamp(Instant.parse("2026-02-10T08:00:00Z"));
        entry.setDate(LocalDate.of(2026, 2, 10));

        PriceHistoryEntryDto dto = mapper.toDto(entry);

        assertThat(dto.price()).isEqualTo(300000);
        assertThat(dto.status()).isEqualTo("asking_price");
        assertThat(dto.timestamp()).isEqualTo(Instant.parse("2026-02-10T08:00:00Z"));
    }
}

package com.kropholler.dev.hermes.funda;

import com.kropholler.dev.hermes.funda.json.FundaProxyListing;
import com.kropholler.dev.hermes.funda.json.FundaProxyPriceChange;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FundaListingMapperTest {

    private final FundaListingMapper mapper = Mappers.getMapper(FundaListingMapper.class);

    @Test
    void toRawListing_usesGlobalIdWhenPresent() {
        FundaProxyListing listing = new FundaProxyListing(
            12345L, "tiny-99", "https://funda.nl/1",
            "Dorpstraat", "10", "A", "1234AB", "Utrecht", "Utrecht",
            300000, 80, 4, 2, "B", "Nice house", null, null, "for_sale", null
        );

        RawListing raw = mapper.toRawListing(listing);

        assertThat(raw.fundaId()).isEqualTo("12345");
    }

    @Test
    void toRawListing_usesTinyIdWhenGlobalIdIsNull() {
        FundaProxyListing listing = new FundaProxyListing(
            null, "tiny-99", "https://funda.nl/1",
            "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht",
            300000, 80, 4, 2, "B", "Nice house", null, null, "for_sale", null
        );

        RawListing raw = mapper.toRawListing(listing);

        assertThat(raw.fundaId()).isEqualTo("tiny-99");
    }

    @Test
    void toRawListing_mapsHouseNumberSuffixToAddition() {
        FundaProxyListing listing = new FundaProxyListing(
            1L, "tiny-1", "https://funda.nl/1",
            "Kerkstraat", "5", "BIS", "2000AB", "Rotterdam", "Zuid-Holland",
            200000, 60, 3, 1, "C", null, null, null, "for_sale", null
        );

        RawListing raw = mapper.toRawListing(listing);

        assertThat(raw.houseNumberAddition()).isEqualTo("BIS");
    }

    @Test
    void toRawListing_mapsStandardFields() {
        FundaProxyListing listing = new FundaProxyListing(
            99L, "tiny-99", "https://funda.nl/99",
            "Hoofdstraat", "3", null, "3000AB", "Amsterdam", "Noord-Holland",
            450000, 120, 6, 4, "A", "Description text", 200, null, "for_sale", null
        );

        RawListing raw = mapper.toRawListing(listing);

        assertThat(raw.street()).isEqualTo("Hoofdstraat");
        assertThat(raw.askingPrice()).isEqualTo(450000);
        assertThat(raw.livingAreaM2()).isEqualTo(120);
        assertThat(raw.plotAreaM2()).isEqualTo(200);
        assertThat(raw.city()).isEqualTo("Amsterdam");
    }

    @Test
    void toRawPriceChange_mapsFieldsExcludingHumanPrice() {
        Instant ts = Instant.parse("2026-03-01T10:00:00Z");
        FundaProxyPriceChange change = new FundaProxyPriceChange(
            320000, "€ 320.000", "asking_price", "funda", LocalDate.of(2026, 3, 1), ts
        );

        RawPriceChange raw = mapper.toRawPriceChange(change);

        assertThat(raw.price()).isEqualTo(320000);
        assertThat(raw.status()).isEqualTo("asking_price");
        assertThat(raw.source()).isEqualTo("funda");
        assertThat(raw.date()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(raw.timestamp()).isEqualTo(ts);
    }
}

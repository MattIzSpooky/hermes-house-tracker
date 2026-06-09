package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class FundaScraperServiceTest {

    private FundaScraperService scraper;
    private String fixtureHtml;

    @BeforeEach
    void setUp() throws IOException {
        scraper = new FundaScraperService();
        fixtureHtml = new String(
            Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/funda-search-result.html")
            ).readAllBytes(),
            StandardCharsets.UTF_8
        );
    }

    @Test
    void parseListings_extractsAllListingsFromHtml() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings).hasSize(2);
    }

    @Test
    void parseListings_extractsCorrectFundaId() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).fundaId()).isEqualTo("12345678");
    }

    @Test
    void parseListings_extractsCorrectUrl() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).url())
            .isEqualTo("https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/");
    }

    @Test
    void parseListings_extractsPrice() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).askingPrice()).isEqualTo(450000);
    }

    @Test
    void parseListings_extractsLivingArea() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).livingAreaM2()).isEqualTo(75);
    }

    @Test
    void parseListings_extractsRooms() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).rooms()).isEqualTo(3);
    }

    @Test
    void parseListings_extractsEnergyLabel() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(0).energyLabel()).isEqualTo("A");
    }

    @Test
    void parseListings_handlesHouseNumberAddition() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");
        assertThat(listings.get(1).houseNumber()).isEqualTo("5");
        assertThat(listings.get(1).houseNumberAddition()).isEqualTo("a");
    }
}

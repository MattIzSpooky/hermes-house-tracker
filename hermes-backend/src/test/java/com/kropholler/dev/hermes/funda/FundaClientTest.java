package com.kropholler.dev.hermes.funda;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(FundaClient.class)
@Import(FundaListingMapperImpl.class)
class FundaClientTest {

    @Autowired FundaClient client;
    @Autowired MockRestServiceServer server;

    private static final String LISTING_JSON = """
        [{
          "global_id": 12345678,
          "tiny_id": "abc",
          "url": "https://funda.nl/koop/amsterdam/huis-12345678/",
          "street": "Dorpstraat",
          "house_number": "1",
          "house_number_suffix": "A",
          "zip_code": "1234AB",
          "city": "Amsterdam",
          "province": "Noord-Holland",
          "asking_price": 350000,
          "living_area_m2": 90,
          "rooms": 4,
          "bedrooms": 2,
          "energy_label": "B",
          "description": "A nice house.",
          "status": "beschikbaar"
        }]
        """;

    private static final String PRICE_HISTORY_JSON = """
        [{
          "price": 360000,
          "human_price": "€ 360.000",
          "status": "asking_price",
          "source": "funda",
          "date": "2025-01-15",
          "timestamp": "2025-01-15T00:00:00Z"
        },{
          "price": 350000,
          "human_price": "€ 350.000",
          "status": "asking_price",
          "source": "funda",
          "date": "2025-03-01",
          "timestamp": "2025-03-01T00:00:00Z"
        }]
        """;

    // --- search ---

    @Test
    void search_buildsUrlWithCityAndPageAndReturnsMappedListings() {
        server.expect(requestTo(containsString("/search")))
            .andExpect(requestTo(containsString("location=Amsterdam")))
            .andExpect(requestTo(containsString("page=1")))
            .andRespond(withSuccess(LISTING_JSON, MediaType.APPLICATION_JSON));

        List<RawListing> results = client.search("Amsterdam", null, null, null, null, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).street()).isEqualTo("Dorpstraat");
        assertThat(results.get(0).city()).isEqualTo("Amsterdam");
        assertThat(results.get(0).askingPrice()).isEqualTo(350000);
    }

    @Test
    void search_withPriceAndAreaFilters_includesThemInUrl() {
        server.expect(requestTo(containsString("min_price=200000")))
            .andExpect(requestTo(containsString("max_price=400000")))
            .andExpect(requestTo(containsString("min_area=80")))
            .andExpect(requestTo(containsString("max_area=150")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<RawListing> results = client.search("Utrecht", 200000, 400000, 80, 150, 1);

        assertThat(results).isEmpty();
    }

    @Test
    void search_nullBody_returnsEmptyList() {
        server.expect(requestTo(containsString("/search")))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        List<RawListing> results = client.search("Rotterdam", null, null, null, null, 1);

        assertThat(results).isEmpty();
    }

    // --- getListing ---

    @Test
    void getListing_found_returnsMappedListing() {
        server.expect(requestTo(containsString("/listings/12345678")))
            .andRespond(withSuccess(LISTING_JSON.replace("[", "").replace("]", ""), MediaType.APPLICATION_JSON));

        Optional<RawListing> result = client.getListing("12345678");

        assertThat(result).isPresent();
        assertThat(result.get().fundaId()).isEqualTo("12345678");
        assertThat(result.get().houseNumberAddition()).isEqualTo("A");
    }

    @Test
    void getListing_notFound_returnsEmpty() {
        server.expect(requestTo(containsString("/listings/99999999")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<RawListing> result = client.getListing("99999999");

        assertThat(result).isEmpty();
    }

    // --- getPriceHistory ---

    @Test
    void getPriceHistory_returnsMappedEntries() {
        server.expect(requestTo(containsString("/listings/12345678/price-history")))
            .andRespond(withSuccess(PRICE_HISTORY_JSON, MediaType.APPLICATION_JSON));

        List<RawPriceChange> history = client.getPriceHistory("12345678");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).price()).isEqualTo(360000);
        assertThat(history.get(1).price()).isEqualTo(350000);
    }

    @Test
    void getPriceHistory_notFound_returnsEmptyList() {
        server.expect(requestTo(containsString("/listings/00000000/price-history")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<RawPriceChange> history = client.getPriceHistory("00000000");

        assertThat(history).isEmpty();
    }

    // --- extractFundaId ---

    @Test
    void extractFundaId_urlWithIdSegment_extractsNumericId() {
        String result = client.extractFundaId("https://funda.nl/koop/amsterdam/12345678/");
        assertThat(result).isEqualTo("12345678");
    }

    @Test
    void extractFundaId_multipleIdSegments_returnsLastMatch() {
        String result = client.extractFundaId("https://funda.nl/koop/1234567/detail/87654321/");
        assertThat(result).isEqualTo("87654321");
    }

    @Test
    void extractFundaId_noDigitSegment_returnsOriginalInput() {
        String result = client.extractFundaId("https://funda.nl/koop/amsterdam/");
        assertThat(result).isEqualTo("https://funda.nl/koop/amsterdam/");
    }
}

package com.kropholler.dev.hermes.listing.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(NominatimClient.class)
class NominatimClientTest {

    @Autowired
    private NominatimClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void geocodeAddress_returnsParsedLatLon() {
        server.expect(requestTo(containsString("Rentmeesterlaan")))
            .andRespond(withSuccess("""
                [{"lat":"51.2574224","lon":"5.6972390",
                  "boundingbox":["51.2573724","51.2574724","5.6971890","5.6972890"],
                  "place_rank":30,"addresstype":"place","display_name":"9, Rentmeesterlaan, Weert"}]
                """, MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeAddress("9", "Rentmeesterlaan", "Weert");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo("51.2574224");
        assertThat(result.get().lon()).isEqualTo("5.6972390");
        assertThat(result.get().boundingbox()).hasSize(4);
    }

    @Test
    void geocodeCity_filtersToNetherlandsAndReturnsFirst() {
        server.expect(requestTo(containsString("countrycodes=nl")))
            .andRespond(withSuccess("""
                [{"lat":"51.2355829","lon":"5.7050797",
                  "boundingbox":["51.1804207","51.2905755","5.5660454","5.7917701"],
                  "place_rank":14,"addresstype":"municipality","display_name":"Weert, Limburg, Netherlands"}]
                """, MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().addressType()).isEqualTo("municipality");
    }

    @Test
    void geocodeAddress_emptyResponse_returnsEmpty() {
        server.expect(requestTo(containsString("search")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeAddress("1", "Onbekend", "Nergens");

        assertThat(result).isEmpty();
    }
}

package com.kropholler.dev.hermes.scraping.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FundaProxyClientTest {

    private MockRestServiceServer server;
    private FundaProxyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FundaProxyClient(builder, "http://test");
    }

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void search_forwardsCorrelationIdHeaderFromMdc() {
        MDC.put("correlationId", "req-abc");
        server.expect(requestTo(containsString("/search")))
              .andExpect(header("X-Correlation-ID", "req-abc"))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search("amsterdam", null, null, null, null, 1);

        server.verify();
    }

    @Test
    void search_omitsCorrelationIdHeaderWhenMdcEmpty() {
        server.expect(requestTo(containsString("/search")))
              .andExpect(headerDoesNotExist("X-Correlation-ID"))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search("amsterdam", null, null, null, null, 1);

        server.verify();
    }
}

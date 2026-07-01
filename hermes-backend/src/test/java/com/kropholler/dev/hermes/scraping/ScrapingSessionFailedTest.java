package com.kropholler.dev.hermes.scraping;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapingSessionFailedTest {

    @Test
    void record_storesSessionIdAndReason() {
        UUID id = UUID.randomUUID();
        ScrapingSessionFailed failed = new ScrapingSessionFailed(id, "Proxy timeout");

        assertThat(failed.sessionId()).isEqualTo(id);
        assertThat(failed.reason()).isEqualTo("Proxy timeout");
    }
}

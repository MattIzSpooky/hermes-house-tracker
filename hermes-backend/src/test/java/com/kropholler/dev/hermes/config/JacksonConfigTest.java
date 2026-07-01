package com.kropholler.dev.hermes.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final JacksonConfig config = new JacksonConfig();

    @Test
    void objectMapper_disablesWriteDatesAsTimestamps() {
        ObjectMapper mapper = config.objectMapper();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    void objectMapper_disablesFailOnUnknownProperties() {
        ObjectMapper mapper = config.objectMapper();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }
}

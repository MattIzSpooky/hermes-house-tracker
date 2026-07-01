package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void instantiation_succeeds() {
        assertThat(new AsyncConfig()).isNotNull();
    }
}

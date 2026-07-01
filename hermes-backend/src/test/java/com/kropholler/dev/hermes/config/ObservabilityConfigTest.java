package com.kropholler.dev.hermes.config;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ObservabilityConfigTest {

    @Mock OpenTelemetry openTelemetry;

    private final ObservabilityConfig config = new ObservabilityConfig();

    @Test
    void otelAppenderInstaller_returnsNonNullListener() {
        ApplicationListener<ApplicationReadyEvent> listener = config.otelAppenderInstaller(openTelemetry);

        assertThat(listener).isNotNull();
    }

}

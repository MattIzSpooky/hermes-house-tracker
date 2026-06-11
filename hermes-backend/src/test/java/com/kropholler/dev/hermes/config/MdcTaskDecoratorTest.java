package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void propagatesMdcSnapshotIntoDecoratedRunnable() {
        MDC.put("correlationId", "ctx-id");
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.get("correlationId")));
        MDC.clear(); // simulate thread switch

        decorated.run();

        assertThat(captured.get()).isEqualTo("ctx-id");
    }

    @Test
    void clearsMdcAfterDecoratedRunnableCompletes() {
        MDC.put("correlationId", "ctx-id");
        Runnable decorated = decorator.decorate(() -> {});
        MDC.clear();

        decorated.run();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void worksWhenNoMdcAtSubmission() {
        MDC.clear();
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.get("correlationId")));
        decorated.run();

        assertThat(captured.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }
}

package com.kropholler.dev.hermes.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            if (snapshot != null) {
                MDC.setContextMap(snapshot);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}

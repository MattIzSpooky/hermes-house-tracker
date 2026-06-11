package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingWorkerTest {

    @Mock ScrapingSessionRepository sessionRepository;
    @Mock FundaProxyClient proxyClient;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ScrapingWorker worker;

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void process_publishesCompletedEventWithCorrelationIdFromMdc() {
        MDC.put("correlationId", "test-corr");
        ScrapingSession session = new ScrapingSession();
        session.setId(UUID.randomUUID());
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(sessionRepository.save(any())).thenReturn(session);
        when(proxyClient.search(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        worker.process(session);

        ArgumentCaptor<ScrapingSessionCompleted> captor = ArgumentCaptor.forClass(ScrapingSessionCompleted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("test-corr");
    }

    @Test
    void process_publishesFailedEventWithCorrelationIdFromMdc() {
        MDC.put("correlationId", "fail-corr");
        ScrapingSession session = new ScrapingSession();
        session.setId(UUID.randomUUID());
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(sessionRepository.save(any())).thenReturn(session);
        when(proxyClient.search(any(), any(), any(), any(), any(), anyInt()))
            .thenThrow(new RuntimeException("network error"));

        worker.process(session);

        ArgumentCaptor<ScrapingSessionFailed> captor = ArgumentCaptor.forClass(ScrapingSessionFailed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("fail-corr");
    }

    @Test
    void process_publishesCompletedEventWithNullCorrelationIdWhenMdcEmpty() {
        ScrapingSession session = new ScrapingSession();
        session.setId(UUID.randomUUID());
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(sessionRepository.save(any())).thenReturn(session);
        when(proxyClient.search(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        worker.process(session);

        ArgumentCaptor<ScrapingSessionCompleted> captor = ArgumentCaptor.forClass(ScrapingSessionCompleted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isNull();
    }
}

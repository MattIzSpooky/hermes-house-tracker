package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingPollerTest {

    @Mock ScrapingSessionRepository sessionRepository;
    @Mock ScrapingWorker worker;
    @InjectMocks ScrapingPoller poller;

    @Test
    void pollQueue_whenSessionPresent_callsWorkerProcess() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/");
        when(sessionRepository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING))
            .thenReturn(Optional.of(session));

        poller.pollQueue();

        verify(worker).process(session);
    }

    @Test
    void pollQueue_whenNoSession_doesNotCallWorker() {
        when(sessionRepository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING))
            .thenReturn(Optional.empty());

        poller.pollQueue();

        verify(worker, never()).process(any());
    }
}

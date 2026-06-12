package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ListingNotFound;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingWorkerTest {

    @Mock private ScrapingSessionRepository sessionRepository;
    @Mock private FundaProxyClient proxyClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ScrapingWorker worker;

    @Test
    void rescrape_publishesListingNotFound_when404() {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        session.setTargetListingUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proxyClient.extractFundaId("https://funda.nl/koop/amsterdam/huis-12345678/"))
            .thenReturn("12345678");
        when(proxyClient.getListing("12345678")).thenReturn(Optional.empty());

        worker.process(session);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(e -> e instanceof ListingNotFound n && "12345678".equals(n.fundaId()));
    }
}

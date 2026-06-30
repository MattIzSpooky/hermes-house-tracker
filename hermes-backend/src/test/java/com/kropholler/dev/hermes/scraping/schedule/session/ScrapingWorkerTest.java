package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.funda.ListingNotFound;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import com.kropholler.dev.hermes.funda.FundaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingWorkerTest {

    @Mock private ScrapingSessionStore sessionStore;
    @Mock private FundaClient proxyClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ScrapingWorker worker;

    @Test
    void rescrape_publishesListingNotFound_when404() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        session.setTargetListingUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
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

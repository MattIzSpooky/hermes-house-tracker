package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.funda.ListingNotFound;
import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import com.kropholler.dev.hermes.funda.FundaClient;
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

    @Test
    void rescrape_withListingFound_completesWithoutPublishingListingNotFound() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        session.setTargetListingUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        session.setFundaUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.extractFundaId("https://funda.nl/koop/amsterdam/huis-12345678/"))
            .thenReturn("12345678");
        when(proxyClient.getListing("12345678")).thenReturn(Optional.of(listing));

        worker.process(session);

        verify(eventPublisher, never()).publishEvent(any(ListingNotFound.class));
        verify(sessionStore).complete(any(), argThat(list -> list.size() == 1));
    }

    @Test
    void process_exceptionInScraping_callsSessionStoreFail() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setTargetListingUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        session.setFundaUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        when(proxyClient.extractFundaId(any())).thenThrow(new RuntimeException("connection error"));

        worker.process(session);

        verify(sessionStore).fail(any(), eq("connection error"));
        verify(sessionStore, never()).complete(any(), any());
    }

    @Test
    void search_multiplePages_stopsWhenPageReturnsEmpty() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(3);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search("amsterdam", null, null, null, null, 0)).thenReturn(List.of(listing));
        when(proxyClient.search("amsterdam", null, null, null, null, 1)).thenReturn(List.of());

        worker.process(session);

        verify(proxyClient, times(2)).search(any(), any(), any(), any(), any(), anyInt());
        verify(sessionStore).complete(any(), argThat(list -> list.size() == 1));
    }

    @Test
    void search_singlePageWithResults_returnsAllListings() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("rotterdam");
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search("rotterdam", null, null, null, null, 0)).thenReturn(List.of(listing));

        worker.process(session);

        verify(sessionStore).complete(any(), argThat(list -> list.size() == 1));
    }

    @Test
    void search_respectsSessionPageLimitWithoutAdditionalClamping() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(8);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search(eq("amsterdam"), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(listing));

        worker.process(session);

        verify(proxyClient, times(8)).search(any(), any(), any(), any(), any(), anyInt());
    }
}

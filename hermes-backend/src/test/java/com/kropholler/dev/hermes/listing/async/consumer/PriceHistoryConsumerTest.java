package com.kropholler.dev.hermes.listing.async.consumer;

import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryUpdated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceHistoryConsumerTest {

    @Mock private PriceHistoryService priceHistoryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PriceHistoryConsumer consumer;

    @Test
    void onMessage_callsFetchAndStoreAndPublishesEvent() {
        UUID listingId = UUID.randomUUID();
        FetchPriceHistoryCommand command = new FetchPriceHistoryCommand(listingId, "12345678");

        consumer.onMessage(command);

        verify(priceHistoryService).fetchAndStore(listingId, "12345678");

        ArgumentCaptor<PriceHistoryUpdated> captor = ArgumentCaptor.forClass(PriceHistoryUpdated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().listingIds()).containsExactly(listingId);
    }

    @Test
    void onMessage_fetchThrows_rethrowsAndDoesNotPublish() {
        UUID listingId = UUID.randomUUID();
        FetchPriceHistoryCommand command = new FetchPriceHistoryCommand(listingId, "12345678");
        doThrow(new RuntimeException("proxy error"))
            .when(priceHistoryService).fetchAndStore(listingId, "12345678");

        assertThatThrownBy(() -> consumer.onMessage(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("proxy error");

        verify(eventPublisher, never()).publishEvent(any());
    }
}

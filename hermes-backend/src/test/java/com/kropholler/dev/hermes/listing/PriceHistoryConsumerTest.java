package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.FetchPriceHistoryCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}

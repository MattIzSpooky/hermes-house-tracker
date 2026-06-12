package com.kropholler.dev.hermes.listing;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.internal.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.internal.JmsQueues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class PriceHistoryConsumer {

    // 10 fetches per minute shared across all 5 consumer threads
    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(10.0 / 60.0);

    private final PriceHistoryService priceHistoryService;
    private final ApplicationEventPublisher eventPublisher;

    @JmsListener(destination = JmsQueues.PRICE_HISTORY_FETCH)
    public void onMessage(FetchPriceHistoryCommand command) {
        RATE_LIMITER.acquire();
        log.debug("Fetching price history for listing {}", command.listingId());
        try {
            priceHistoryService.fetchAndStore(command.listingId(), command.fundaId());
            eventPublisher.publishEvent(new PriceHistoryUpdated(List.of(command.listingId())));
        } catch (Exception e) {
            log.warn("Failed to fetch price history for listing {}: {}", command.listingId(), e.getMessage());
            throw e;
        }
    }
}

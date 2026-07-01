package com.kropholler.dev.hermes.listing.async.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryUpdated;
import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
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
    private static final int AMOUNT_PER_MINUTE = 50;
    private static final double MINUTE_IN_SECONDS = 60;

    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(AMOUNT_PER_MINUTE / MINUTE_IN_SECONDS);

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

package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.listing.internal.JmsQueues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class ListingSummaryGenerationListener {

    private final ListingSummaryGenerationService generationService;

    @JmsListener(destination = JmsQueues.LISTING_SUMMARY_GENERATE)
    public void onMessage(String listingIdStr) {
        UUID listingId;
        try {
            listingId = UUID.fromString(listingIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Received invalid listing ID on summary queue: {}", listingIdStr);
            return;
        }
        try {
            generationService.generate(listingId);
        } catch (Exception e) {
            log.error("Summary generation failed for listing {}", listingId, e);
            throw e;
        }
    }
}

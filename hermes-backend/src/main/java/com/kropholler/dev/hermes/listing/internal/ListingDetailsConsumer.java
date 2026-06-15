package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.scraping.FundaProxyFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ListingDetailsConsumer {

    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(50.0 / 60.0);

    private final ListingRepository listingRepository;
    private final FundaProxyFacade proxyFacade;

    @JmsListener(destination = JmsQueues.LISTING_DETAILS_FETCH)
    @Transactional
    public void onMessage(FetchListingDetailsCommand command) {
        RATE_LIMITER.acquire();
        log.debug("Fetching listing details for {}", command.listingId());
        proxyFacade.getListing(command.fundaId()).ifPresent(raw ->
            listingRepository.findById(command.listingId()).ifPresent(listing -> {
                listing.setDescription(raw.description());
                listing.setLivingAreaM2(raw.livingAreaM2());
                listing.setRooms(raw.rooms());
                listing.setBedrooms(raw.bedrooms());
                listing.setEnergyLabel(raw.energyLabel());
                listing.setPlotAreaM2(raw.plotAreaM2());
                listingRepository.save(listing);
            })
        );
    }
}

package com.kropholler.dev.hermes.listing.async.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchListingDetailsCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.scraping.funda.FundaProxyFacade;
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
        log.info("Fetching listing details for {}", command.listingId());

        var externalListingOptional = proxyFacade.getListing(command.fundaId());

        if (externalListingOptional.isEmpty()) {
            log.error("Proxy could not find listing {}", command.listingId());
            return;
        }

        var listingInDatabaseOptional = listingRepository.findById(command.listingId());

        if (listingInDatabaseOptional.isEmpty()) {
            log.error("Database could not find listing {}", command.listingId());
            return;
        }

        final var externalListing = externalListingOptional.get();
        final var listing = listingInDatabaseOptional.get();

        listing.setDescription(externalListing.description());
        listing.setLivingAreaM2(externalListing.livingAreaM2());
        listing.setRooms(externalListing.rooms());
        listing.setBedrooms(externalListing.bedrooms());
        listing.setEnergyLabel(externalListing.energyLabel());
        listing.setPlotAreaM2(externalListing.plotAreaM2());
        listingRepository.save(listing);

        log.info("Successfully fetched listing details for {}", command.listingId());
    }
}

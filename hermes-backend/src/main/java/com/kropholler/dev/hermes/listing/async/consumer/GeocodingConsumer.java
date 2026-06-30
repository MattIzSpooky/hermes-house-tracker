package com.kropholler.dev.hermes.listing.async.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class GeocodingConsumer {

    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(1.0);

    private final ListingRepository listingRepository;
    private final GeocodingService geocodingService;

    @JmsListener(destination = JmsQueues.GEOCODING_FETCH)
    @Transactional
    public void onMessage(FetchGeocodingCommand command) {
        RATE_LIMITER.acquire();
        log.info("Fetching listing geolocation for {}", command.listingId());

        final var listingOptional = listingRepository.findById(command.listingId());

        if (listingOptional.isEmpty()) {
            log.error("Could not find listing {}", command.listingId());
            return;
        }

        final var listing = listingOptional.get();

        if (listing.getStreet() == null || listing.getCity() == null) {
            log.error("Listing {} has no street or city", command.listingId());
            return;
        }

        final var geocodingResultOptional = geocodingService.geocodeAddress(
                listing.getHouseNumber(),
                listing.getStreet(),
                listing.getCity()
        );

        if (geocodingResultOptional.isEmpty()) {
            log.error("Could not find geo location for {}", command.listingId());
            return;
        }

        final var response = geocodingResultOptional.get();

        double lon = response.lon();
        double lat = response.lat();

        listingRepository.updateLocation(command.listingId(), lon, lat);
        listingRepository.updateBoundingBox(command.listingId(),
                response.boundingBoxLonMin(),
                response.boundingBoxLatMin(),
                response.boundingBoxLonMax(),
                response.boundingBoxLatMax()
        );
        log.info("Successfully geocoded listing {} to {},{}", command.listingId(), lat, lon);
    }
}

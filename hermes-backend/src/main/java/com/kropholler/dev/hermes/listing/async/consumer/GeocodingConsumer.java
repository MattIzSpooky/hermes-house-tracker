package com.kropholler.dev.hermes.listing.async.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.geocoding.NominatimClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class GeocodingConsumer {

    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(1.0);

    private final ListingRepository listingRepository;
    private final NominatimClient nominatimClient;

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

        final var geocodingResultOptional = nominatimClient.geocodeAddress( listing.getHouseNumber(), listing.getStreet(), listing.getCity());

        if (geocodingResultOptional.isEmpty()) {
            log.error("Could not find geo location for {}", command.listingId());
            return;
        }

        final var response = geocodingResultOptional.get();

        double lon = Double.parseDouble(response.lon());
        double lat = Double.parseDouble(response.lat());
        listingRepository.updateLocation(command.listingId(), lon, lat);
        updateBoundingBox(command.listingId(), response.boundingbox());
        log.info("Successfully geocoded listing {} to {},{}", command.listingId(), lat, lon);
    }

    private void updateBoundingBox(java.util.UUID listingId, List<String> bbox) {
        if (bbox == null || bbox.size() < 4) return;
        double latMin = Double.parseDouble(bbox.get(0));
        double latMax = Double.parseDouble(bbox.get(1));
        double lonMin = Double.parseDouble(bbox.get(2));
        double lonMax = Double.parseDouble(bbox.get(3));
        listingRepository.updateBoundingBox(listingId, lonMin, latMin, lonMax, latMax);
    }
}

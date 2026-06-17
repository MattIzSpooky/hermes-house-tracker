package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
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
        listingRepository.findById(command.listingId()).ifPresent(listing -> {
            if (listing.getStreet() == null || listing.getCity() == null) return;
            nominatimClient.geocodeAddress(
                listing.getHouseNumber(), listing.getStreet(), listing.getCity()
            ).ifPresent(response -> {
                double lon = Double.parseDouble(response.lon());
                double lat = Double.parseDouble(response.lat());
                listingRepository.updateLocation(command.listingId(), lon, lat);
                updateBoundingBox(command.listingId(), response.boundingbox());
                log.debug("Geocoded listing {} to {},{}", command.listingId(), lat, lon);
            });
        });
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

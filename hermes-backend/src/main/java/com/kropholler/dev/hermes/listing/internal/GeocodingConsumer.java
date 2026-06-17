package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
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

    static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

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
                listing.setLocation(toPoint(response.lat(), response.lon()));
                listing.setBoundingBox(toBoundingBox(response.boundingbox()));
                listingRepository.save(listing);
                log.debug("Geocoded listing {} to {},{}", command.listingId(), response.lat(), response.lon());
            });
        });
    }

    static Point toPoint(String lat, String lon) {
        Point point = GEOMETRY_FACTORY.createPoint(
            new Coordinate(Double.parseDouble(lon), Double.parseDouble(lat)));
        point.setSRID(4326);
        return point;
    }

    static Polygon toBoundingBox(List<String> bbox) {
        if (bbox == null || bbox.size() < 4) return null;
        double latMin = Double.parseDouble(bbox.get(0));
        double latMax = Double.parseDouble(bbox.get(1));
        double lonMin = Double.parseDouble(bbox.get(2));
        double lonMax = Double.parseDouble(bbox.get(3));
        Coordinate[] coords = {
            new Coordinate(lonMin, latMin),
            new Coordinate(lonMax, latMin),
            new Coordinate(lonMax, latMax),
            new Coordinate(lonMin, latMax),
            new Coordinate(lonMin, latMin)
        };
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    }
}

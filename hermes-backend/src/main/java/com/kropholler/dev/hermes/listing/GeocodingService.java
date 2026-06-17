package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.City;
import com.kropholler.dev.hermes.listing.internal.CityRepository;
import com.kropholler.dev.hermes.listing.internal.NominatimClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private final CityRepository cityRepository;
    private final NominatimClient nominatimClient;

    @Transactional
    public Optional<City> findOrFetchCity(String cityName) {
        Optional<City> cached = cityRepository.findByNameIgnoreCase(cityName);
        if (cached.isPresent()) return cached;

        return nominatimClient.geocodeCity(cityName).map(response -> {
            City city = new City();
            city.setName(cityName);
            city.setLocation(toPoint(response.lat(), response.lon()));
            city.setBoundingBox(toBoundingBox(response.boundingbox()));
            city.setFetchedAt(Instant.now());
            return cityRepository.save(city);
        });
    }

    public Optional<double[]> geocodeAddress(String houseNumber, String street, String city) {
        return nominatimClient.geocodeAddress(houseNumber, street, city)
            .map(r -> new double[]{Double.parseDouble(r.lat()), Double.parseDouble(r.lon())});
    }

    private static Point toPoint(String lat, String lon) {
        Point point = GEOMETRY_FACTORY.createPoint(
            new Coordinate(Double.parseDouble(lon), Double.parseDouble(lat)));
        point.setSRID(4326);
        return point;
    }

    private static Polygon toBoundingBox(List<String> bbox) {
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

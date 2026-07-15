package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.city.CityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final CityRepository cityRepository;
    private final NominatimClient nominatimClient;

    @Transactional
    public Optional<CityEntity> findOrFetchCity(String cityName) {
        return findOrFetchCityEntity(cityName);
    }

    public Optional<GeocodeResult> geocodeAddress(String houseNumber, String street, String city) {
        log.debug("geocodeAddress called: houseNumber={}, street={}, city={}", houseNumber, street, city);
        Optional<GeocodeResult> result = nominatimClient.geocodeAddress(houseNumber, street, city)
            .map(r -> new GeocodeResult(Double.parseDouble(r.lon()), Double.parseDouble(r.lat()), r.boundingbox()));
        if (result.isEmpty()) {
            log.warn("geocodeAddress: no result for houseNumber={}, street={}, city={}", houseNumber, street, city);
        }
        return result;
    }

    @Transactional
    public Optional<GeocodeResult> geocodeCity(String cityName) {
        return findOrFetchCityEntity(cityName)
            .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null));
    }

    private Optional<CityEntity> findOrFetchCityEntity(String cityName) {
        Optional<CityEntity> cached = cityRepository.findByNameIgnoreCase(cityName);
        if (cached.isPresent()) {
            log.debug("findOrFetchCity cache hit for '{}'", cityName);
            return cached;
        }

        log.info("findOrFetchCity cache miss for '{}', fetching from Nominatim", cityName);
        Optional<CityEntity> fetched = nominatimClient.geocodeCity(cityName).map(response -> {
            CityEntity city = new CityEntity();
            city.setName(cityName);
            city.setLongitude(Double.parseDouble(response.lon()));
            city.setLatitude(Double.parseDouble(response.lat()));
            city.setFetchedAt(Instant.now());
            return cityRepository.save(city);
        });
        if (fetched.isEmpty()) {
            log.warn("findOrFetchCity: Nominatim returned no result for '{}'", cityName);
        }
        return fetched;
    }

}

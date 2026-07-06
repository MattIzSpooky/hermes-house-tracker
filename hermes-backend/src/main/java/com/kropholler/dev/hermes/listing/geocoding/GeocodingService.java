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
        Optional<CityEntity> cached = cityRepository.findByNameIgnoreCase(cityName);
        if (cached.isPresent()) return cached;

        return nominatimClient.geocodeCity(cityName).map(response -> {
            CityEntity city = new CityEntity();
            city.setName(cityName);
            city.setLongitude(Double.parseDouble(response.lon()));
            city.setLatitude(Double.parseDouble(response.lat()));
            city.setFetchedAt(Instant.now());
            return cityRepository.save(city);
        });
    }

    public Optional<GeocodeResult> geocodeAddress(String houseNumber, String street, String city) {
        return nominatimClient.geocodeAddress(houseNumber, street, city)
            .map(r -> new GeocodeResult(Double.parseDouble(r.lon()), Double.parseDouble(r.lat()), r.boundingbox()));
    }

    @Transactional
    public Optional<GeocodeResult> geocodeCity(String cityName) {
        return findOrFetchCity(cityName)
            .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null));
    }

}

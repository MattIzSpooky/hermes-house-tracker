package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.city.City;
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
    public Optional<City> findOrFetchCity(String cityName) {
        Optional<City> cached = cityRepository.findByNameIgnoreCase(cityName);
        if (cached.isPresent()) return cached;

        return nominatimClient.geocodeCity(cityName).map(response -> {
            City city = new City();
            city.setName(cityName);
            city.setLatitude(Double.parseDouble(response.lat()));
            city.setLongitude(Double.parseDouble(response.lon()));
            city.setFetchedAt(Instant.now());
            return cityRepository.save(city);
        });
    }

    public Optional<double[]> geocodeAddress(String houseNumber, String street, String city) {
        return nominatimClient.geocodeAddress(houseNumber, street, city)
            .map(r -> new double[]{Double.parseDouble(r.lat()), Double.parseDouble(r.lon())});
    }

}

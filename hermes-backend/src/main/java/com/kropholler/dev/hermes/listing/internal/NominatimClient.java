package com.kropholler.dev.hermes.listing.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
class NominatimClient {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "HermesHouseTracker/1.0 (https://github.com/MattIzSpooky/hermes-house-tracker)";

    private final RestClient restClient;

    NominatimClient(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl(BASE_URL)
            .defaultHeader("User-Agent", USER_AGENT)
            .build();
    }

    Optional<NominatimResponse> geocodeAddress(String houseNumber, String street, String city) {
        String query = houseNumber + " " + street + " " + city;
        log.debug("Geocoding address: {}", query);
        try {
            List<NominatimResponse> results = restClient.get()
                .uri("/search?q={q}&format=jsonv2&limit=1", query)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            return results == null || results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Nominatim address geocoding failed for '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    Optional<NominatimResponse> geocodeCity(String cityName) {
        log.debug("Geocoding city: {}", cityName);
        try {
            List<NominatimResponse> results = restClient.get()
                .uri("/search?q={q}&format=jsonv2&countrycodes=nl&limit=5", cityName)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            if (results == null || results.isEmpty()) return Optional.empty();
            return results.stream()
                .filter(r -> List.of("municipality", "city", "town", "administrative").contains(r.addressType()))
                .findFirst()
                .or(() -> Optional.of(results.get(0)));
        } catch (Exception e) {
            log.warn("Nominatim city geocoding failed for '{}': {}", cityName, e.getMessage());
            return Optional.empty();
        }
    }
}

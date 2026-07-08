package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository repository;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public AddressDto getProfile(UUID userId) {
        return repository.findById(userId)
            .map(UserProfileService::toDto)
            .orElseGet(AddressDto::empty);
    }

    @Transactional
    public AddressDto updateAddress(UUID userId, String street, String houseNumber,
            String houseNumberAddition, String zipCode, String city, String province) {
        log.info("updateAddress called: userId={}, street={}, houseNumber={}, city={}", userId, street, houseNumber, city);
        GeocodeResult geocodeResult = geocodingService.geocodeAddress(houseNumber, street, city)
            .orElseThrow(() -> {
                log.warn("updateAddress: geocoding failed for userId={}, street={}, houseNumber={}, city={}", userId, street, houseNumber, city);
                return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Address could not be geocoded");
            });

        UserProfileEntity entity = repository.findById(userId).orElseGet(() -> {
            UserProfileEntity e = new UserProfileEntity();
            e.setUserId(userId);
            return e;
        });
        entity.setStreet(street);
        entity.setHouseNumber(houseNumber);
        entity.setHouseNumberAddition(houseNumberAddition);
        entity.setZipCode(zipCode);
        entity.setCity(city);
        entity.setProvince(province);
        entity.setLatitude(geocodeResult.lat());
        entity.setLongitude(geocodeResult.lon());
        entity.setUpdatedAt(Instant.now());

        AddressDto dto = toDto(repository.save(entity));
        log.info("Address updated for user {}", userId);
        return dto;
    }

    @Transactional
    public void syncEmail(UUID userId, String email) {
        if (email == null || email.isBlank()) return;
        int updated = repository.updateEmail(userId, email);
        if (updated == 0) {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(userId);
            entity.setEmail(email);
            entity.setUpdatedAt(Instant.now());
            try {
                repository.save(entity);
                log.debug("syncEmail created new profile row for user {}", userId);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with a concurrent syncEmail call for the same new user — the row
                // now exists, so retry as an update instead of failing the request.
                log.debug("syncEmail lost race creating profile for user {}, retrying as update", userId);
                repository.updateEmail(userId, email);
            }
        }
    }

    private static AddressDto toDto(UserProfileEntity entity) {
        return new AddressDto(
            entity.getStreet(),
            entity.getHouseNumber(),
            entity.getHouseNumberAddition(),
            entity.getZipCode(),
            entity.getCity(),
            entity.getProvince(),
            entity.getLatitude(),
            entity.getLongitude()
        );
    }
}

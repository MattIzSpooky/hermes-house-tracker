package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

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
        GeocodeResult geocodeResult = geocodingService.geocodeAddress(houseNumber, street, city)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Address could not be geocoded"));

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

        return toDto(repository.save(entity));
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

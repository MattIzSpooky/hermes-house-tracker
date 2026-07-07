package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    UserProfileRepository repository;
    @Mock
    GeocodingService geocodingService;

    UserProfileService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new UserProfileService(repository, geocodingService);
    }

    @Test
    void getProfile_returnsAllNullWhenNoRowExists() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        AddressDto result = service.getProfile(userId);

        assertThat(result.street()).isNull();
        assertThat(result.houseNumber()).isNull();
        assertThat(result.city()).isNull();
        assertThat(result.latitude()).isNull();
        assertThat(result.longitude()).isNull();
    }

    @Test
    void getProfile_returnsMappedDtoWhenRowExists() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setStreet("Dorpstraat");
        entity.setHouseNumber("10");
        entity.setCity("Utrecht");
        entity.setLatitude(52.09);
        entity.setLongitude(5.12);
        when(repository.findById(userId)).thenReturn(Optional.of(entity));

        AddressDto result = service.getProfile(userId);

        assertThat(result.street()).isEqualTo("Dorpstraat");
        assertThat(result.houseNumber()).isEqualTo("10");
        assertThat(result.city()).isEqualTo("Utrecht");
        assertThat(result.latitude()).isEqualTo(52.09);
        assertThat(result.longitude()).isEqualTo(5.12);
    }

    @Test
    void updateAddress_savesGeocodedAddressOnSuccess() {
        UUID userId = UUID.randomUUID();
        GeocodeResult geocodeResult = new GeocodeResult(5.12, 52.09, List.of("52.0", "52.2", "5.0", "5.2"));
        when(geocodingService.geocodeAddress("10", "Dorpstraat", "Utrecht"))
            .thenReturn(Optional.of(geocodeResult));
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddressDto result = service.updateAddress(
            userId, "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht");

        assertThat(result.street()).isEqualTo("Dorpstraat");
        assertThat(result.latitude()).isEqualTo(52.09);
        assertThat(result.longitude()).isEqualTo(5.12);

        ArgumentCaptor<UserProfileEntity> cap = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        assertThat(cap.getValue().getStreet()).isEqualTo("Dorpstraat");
        assertThat(cap.getValue().getLatitude()).isEqualTo(52.09);
        assertThat(cap.getValue().getLongitude()).isEqualTo(5.12);
        assertThat(cap.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void updateAddress_updatesExistingRowInPlace() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Oude straat");
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        GeocodeResult geocodeResult = new GeocodeResult(5.12, 52.09, List.of("52.0", "52.2", "5.0", "5.2"));
        when(geocodingService.geocodeAddress("10", "Dorpstraat", "Utrecht"))
            .thenReturn(Optional.of(geocodeResult));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateAddress(userId, "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht");

        ArgumentCaptor<UserProfileEntity> cap = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue()).isSameAs(existing);
        assertThat(cap.getValue().getStreet()).isEqualTo("Dorpstraat");
    }

    @Test
    void updateAddress_throws422AndNeverSavesWhenGeocodingFails() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("999", "Nonexistent Street", "Nowhereville"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAddress(
                userId, "Nonexistent Street", "999", null, "0000ZZ", "Nowhereville", "Utrecht"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be geocoded");

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void syncEmail_existingRow_updatesViaJpql() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(1);

        service.syncEmail(userId, "user@hermes.local");

        verify(repository).updateEmail(userId, "user@hermes.local");
        verify(repository, never()).save(any());
    }

    @Test
    void syncEmail_noExistingRow_insertsNewProfile() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(0);

        service.syncEmail(userId, "user@hermes.local");

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@hermes.local");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void syncEmail_raceOnInsert_retriesAsUpdate() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(0);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        service.syncEmail(userId, "user@hermes.local");

        verify(repository, times(2)).updateEmail(userId, "user@hermes.local");
    }

    @Test
    void syncEmail_nullEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), null);

        verifyNoInteractions(repository);
    }

    @Test
    void syncEmail_blankEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), "   ");

        verifyNoInteractions(repository);
    }
}

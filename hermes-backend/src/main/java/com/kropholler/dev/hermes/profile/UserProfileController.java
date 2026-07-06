package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.profile.openapi.AddressResponse;
import com.kropholler.dev.hermes.profile.openapi.ProfileApi;
import com.kropholler.dev.hermes.profile.openapi.UpdateAddressRequest;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserProfileController implements ProfileApi {

    private final UserProfileService userProfileService;
    private final UserProfileApiMapper userProfileApiMapper;

    @Override
    public ResponseEntity<AddressResponse> getProfile() {
        AddressDto dto = userProfileService.getProfile(CurrentUser.current().id());
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }

    @Override
    public ResponseEntity<AddressResponse> updateAddress(UpdateAddressRequest request) {
        CurrentUser currentUser = CurrentUser.current();
        AddressDto dto = userProfileService.updateAddress(
            currentUser.id(),
            request.getStreet(),
            request.getHouseNumber(),
            request.getHouseNumberAddition(),
            request.getZipCode(),
            request.getCity(),
            request.getProvince()
        );
        userProfileService.syncEmail(currentUser.id(), currentUser.email());
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }
}

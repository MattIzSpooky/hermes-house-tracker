package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.profile.openapi.AddressResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserProfileApiMapper {

    AddressResponse toResponse(AddressDto dto);
}

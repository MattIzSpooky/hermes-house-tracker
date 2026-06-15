package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.ListingDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
interface ChatListingCardMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"fundaId", "zipCode", "firstSeenAt", "lastSeenAt", "description", "rooms", "plotAreaM2", "status"})
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : null)")
    ChatListingCard toChatListingCard(ListingDto dto);
}

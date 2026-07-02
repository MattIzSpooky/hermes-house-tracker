package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.ListingDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface ChatListingCardMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"fundaId", "url", "zipCode", "firstSeenAt", "lastSeenAt", "description", "rooms", "plotAreaM2", "status", "location"})
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : null)")
    ChatListingCard toChatListingCard(ListingDto dto);
}

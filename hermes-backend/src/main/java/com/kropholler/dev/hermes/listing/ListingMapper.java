package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntry;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
interface ListingMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"lastUpdatedAt", "deletedAt"})
    ListingDto toDto(Listing listing, Integer currentPrice);

    @BeanMapping(ignoreUnmappedSourceProperties = {"listingId"})
    PriceHistoryEntryDto toDto(PriceHistoryEntry entry);
}

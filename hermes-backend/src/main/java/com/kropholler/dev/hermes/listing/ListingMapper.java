package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
interface ListingMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"lastUpdatedAt", "deletedAt"})
    ListingDto toDto(ListingEntity listing, Integer currentPrice);

    @BeanMapping(ignoreUnmappedSourceProperties = {"listingId"})
    PriceHistoryEntryDto toDto(PriceHistoryEntryEntity entry);
}

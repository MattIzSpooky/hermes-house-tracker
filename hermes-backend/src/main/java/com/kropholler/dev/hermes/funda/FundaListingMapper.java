package com.kropholler.dev.hermes.funda;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.funda.json.FundaProxyListing;
import com.kropholler.dev.hermes.funda.json.FundaProxyPriceChange;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
interface FundaListingMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"globalId", "tinyId", "publicationDate", "offeringType"})
    @Mapping(target = "fundaId", expression = "java(p.globalId() != null ? p.globalId().toString() : p.tinyId())")
    @Mapping(target = "houseNumberAddition", source = "houseNumberSuffix")
    RawListing toRawListing(FundaProxyListing p);

    @BeanMapping(ignoreUnmappedSourceProperties = {"humanPrice"})
    RawPriceChange toRawPriceChange(FundaProxyPriceChange p);
}

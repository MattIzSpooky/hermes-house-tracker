package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.scraping.internal.ScrapingSession;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
interface ScrapingSessionMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {
        "city", "minPrice", "maxPrice", "minArea", "maxArea",
        "pageLimit", "fundaUrl", "targetListingUrl", "startedAt"
    })
    ScrapingSessionDto toDto(ScrapingSession session);
}

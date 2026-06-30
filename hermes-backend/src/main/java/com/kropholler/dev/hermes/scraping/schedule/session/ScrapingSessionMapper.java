package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ScrapingSessionMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {
        "city", "minPrice", "maxPrice", "minArea", "maxArea",
        "pageLimit", "fundaUrl", "targetListingUrl", "startedAt"
    })
    ScrapingSessionDto toDto(ScrapingSessionEntity session);
}

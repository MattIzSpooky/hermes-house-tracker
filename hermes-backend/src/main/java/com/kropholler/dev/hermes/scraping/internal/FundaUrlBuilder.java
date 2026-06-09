package com.kropholler.dev.hermes.scraping.internal;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FundaUrlBuilder {

    private static final String BASE_URL = "https://www.funda.nl/zoeken/koop";
    private static final int MAX_PAGE = 5;

    String build(String city, Integer minPrice, Integer maxPrice,
                 Integer minArea, Integer maxArea, int page) {
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 1 and " + MAX_PAGE);
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(BASE_URL)
            .queryParam("selected_area", "[\"" + city.toLowerCase() + "\"]")
            .queryParam("search_result", page);

        if (minPrice != null) uri.queryParam("price_min", minPrice);
        if (maxPrice != null) uri.queryParam("price_max", maxPrice);
        if (minArea != null)  uri.queryParam("floor_area_min", minArea);
        if (maxArea != null)  uri.queryParam("floor_area_max", maxArea);

        return uri.build().encode().toUriString();
    }
}

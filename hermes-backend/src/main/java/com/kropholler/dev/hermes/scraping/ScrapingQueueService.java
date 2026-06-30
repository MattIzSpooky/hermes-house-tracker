package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSession;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionMapper;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScrapingQueueService {

    private static final int MAX_PAGE_LIMIT = 5;

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingSessionMapper mapper;

    @Transactional
    public ScrapingSessionDto enqueueSearch(String city, Integer minPrice, Integer maxPrice,
                                            Integer minArea, Integer maxArea, int pageLimit) {
        int clampedLimit = Math.min(pageLimit, MAX_PAGE_LIMIT);
        String url = buildSearchUrl(city, minPrice, maxPrice, minArea, maxArea, 1);

        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity(city);
        session.setMinPrice(minPrice);
        session.setMaxPrice(maxPrice);
        session.setMinArea(minArea);
        session.setMaxArea(maxArea);
        session.setPageLimit(clampedLimit);
        session.setFundaUrl(url);

        return mapper.toDto(sessionRepository.save(session));
    }

    @Transactional
    public ScrapingSessionDto enqueueRescrape(String listingUrl, String city) {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity(city);
        session.setPageLimit(1);
        session.setFundaUrl(listingUrl);
        session.setTargetListingUrl(listingUrl);

        return mapper.toDto(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public Optional<ScrapingSessionDto> findById(UUID id) {
        return sessionRepository.findById(id).map(mapper::toDto);
    }

    private String buildSearchUrl(String city, Integer minPrice, Integer maxPrice,
                                  Integer minArea, Integer maxArea, int page) {
        UriComponentsBuilder uri = UriComponentsBuilder
            .fromUriString("https://www.funda.nl/zoeken/koop")
            .queryParam("selected_area", "[\"" + city.toLowerCase() + "\"]")
            .queryParam("search_result", page);
        if (minPrice != null) uri.queryParam("price_min", minPrice);
        if (maxPrice != null) uri.queryParam("price_max", maxPrice);
        if (minArea != null)  uri.queryParam("floor_area_min", minArea);
        if (maxArea != null)  uri.queryParam("floor_area_max", maxArea);
        return uri.build().encode().toUriString();
    }

}

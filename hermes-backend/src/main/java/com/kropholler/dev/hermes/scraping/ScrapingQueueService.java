package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionEntity;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionMapper;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapingQueueService {

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingSessionMapper mapper;

    @Value("${scraping.page-limit.max:5}")
    private int maxPageLimit = 5;

    @Transactional
    public ScrapingSessionDto enqueueSearch(String city, Integer minPrice, Integer maxPrice,
                                            Integer minArea, Integer maxArea, int pageLimit) {
        int clampedLimit = Math.min(pageLimit, maxPageLimit);
        String url = buildSearchUrl(city, minPrice, maxPrice, minArea, maxArea, 1);
        log.info("enqueueSearch: city={}, minPrice={}, maxPrice={}, pageLimit={} (clamped to {})",
                city, minPrice, maxPrice, pageLimit, clampedLimit);

        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity(city);
        session.setMinPrice(minPrice);
        session.setMaxPrice(maxPrice);
        session.setMinArea(minArea);
        session.setMaxArea(maxArea);
        session.setPageLimit(clampedLimit);
        session.setFundaUrl(url);

        ScrapingSessionDto dto = mapper.toDto(sessionRepository.save(session));
        log.info("enqueueSearch created session {}", dto.id());
        return dto;
    }

    @Transactional
    public ScrapingSessionDto enqueueRescrape(String listingUrl, String city) {
        log.info("enqueueRescrape: city={}, listingUrl={}", city, listingUrl);
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity(city);
        session.setPageLimit(1);
        session.setFundaUrl(listingUrl);
        session.setTargetListingUrl(listingUrl);

        ScrapingSessionDto dto = mapper.toDto(sessionRepository.save(session));
        log.info("enqueueRescrape created session {}", dto.id());
        return dto;
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

package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
class FundaProxyClient {

    private static final Pattern ID_PATTERN = Pattern.compile("-(\\d+)-");
    private static final ParameterizedTypeReference<List<FundaProxyListing>> LISTING_LIST =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    FundaProxyClient(RestClient.Builder builder,
                     @Value("${funda.proxy.url:http://funda-proxy:8001}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    List<RawListing> search(String city, Integer minPrice, Integer maxPrice,
                            Integer minArea, Integer maxArea, int page) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/search")
            .queryParam("location", city)
            .queryParam("page", page);
        if (minPrice != null) uri.queryParam("min_price", minPrice);
        if (maxPrice != null) uri.queryParam("max_price", maxPrice);
        if (minArea  != null) uri.queryParam("min_area",  minArea);
        if (maxArea  != null) uri.queryParam("max_area",  maxArea);

        String uriString = uri.build().toUriString();
        log.info("Calling funda-proxy: GET {}", uriString);

        List<FundaProxyListing> results = restClient.get()
            .uri(uriString)
            .retrieve()
            .body(LISTING_LIST);

        return results == null ? List.of() : results.stream().map(this::toRawListing).toList();
    }

    Optional<RawListing> getListing(String fundaId) {
        log.info("Calling funda-proxy: GET /listings/{}", fundaId);
        try {
            FundaProxyListing listing = restClient.get()
                .uri("/listings/{id}", fundaId)
                .retrieve()
                .body(FundaProxyListing.class);
            return Optional.ofNullable(listing).map(this::toRawListing);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Listing {} not found in funda-proxy", fundaId);
                return Optional.empty();
            }
            throw e;
        }
    }

    String extractFundaId(String listingUrl) {
        Matcher m = ID_PATTERN.matcher(listingUrl);
        return m.find() ? m.group(1) : listingUrl;
    }

    private RawListing toRawListing(FundaProxyListing p) {
        return new RawListing(
            p.globalId() != null ? p.globalId().toString() : p.tinyId(),
            p.url(),
            p.street(),
            p.houseNumber(),
            p.houseNumberSuffix(),
            p.zipCode(),
            p.city(),
            p.province(),
            p.askingPrice(),
            p.livingAreaM2(),
            p.rooms(),
            p.energyLabel(),
            parseDate(p.publicationDate()),
            p.status()
        );
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }
}

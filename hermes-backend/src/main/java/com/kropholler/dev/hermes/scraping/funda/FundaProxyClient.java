package com.kropholler.dev.hermes.scraping.funda;

import com.kropholler.dev.hermes.scraping.funda.json.FundaProxyListing;
import com.kropholler.dev.hermes.scraping.funda.json.FundaProxyPriceChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FundaProxyClient {

    private static final Pattern ID_PATTERN = Pattern.compile("/(\\d{7,9})(?:/|$)");
    private static final ParameterizedTypeReference<List<FundaProxyListing>> LISTING_LIST =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<FundaProxyPriceChange>> PRICE_CHANGE_LIST =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final FundaProxyListingMapper mapper;

    FundaProxyClient(RestClient.Builder builder,
                     @Value("${funda.proxy.url:http://funda-proxy:8001}") String baseUrl,
                     FundaProxyListingMapper mapper) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    public List<RawListing> search(String city, Integer minPrice, Integer maxPrice,
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

        return results == null ? List.of() : results.stream().map(mapper::toRawListing).toList();
    }

    public Optional<RawListing> getListing(String fundaId) {
        log.info("Calling funda-proxy: GET /listings/{}", fundaId);
        try {
            FundaProxyListing listing = restClient.get()
                .uri("/listings/{id}", fundaId)
                .retrieve()
                .body(FundaProxyListing.class);
            return Optional.ofNullable(listing).map(mapper::toRawListing);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Listing {} not found in funda-proxy", fundaId);
                return Optional.empty();
            }
            throw e;
        }
    }

    public List<RawPriceChange> getPriceHistory(String fundaId) {
        log.info("Calling funda-proxy: GET /listings/{}/price-history", fundaId);
        try {
            List<FundaProxyPriceChange> results = restClient.get()
                .uri("/listings/{id}/price-history", fundaId)
                .retrieve()
                .body(PRICE_CHANGE_LIST);
            return results == null ? List.of()
                : results.stream().map(mapper::toRawPriceChange).toList();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Price history for {} not found in funda-proxy", fundaId);
                return List.of();
            }
            throw e;
        }
    }

    public String extractFundaId(String listingUrl) {
        Matcher m = ID_PATTERN.matcher(listingUrl);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? last : listingUrl;
    }

}

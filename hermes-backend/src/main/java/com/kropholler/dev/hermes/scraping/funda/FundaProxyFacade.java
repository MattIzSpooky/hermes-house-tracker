package com.kropholler.dev.hermes.scraping.funda;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundaProxyFacade {

    private final FundaProxyClient client;

    public List<RawPriceChange> getPriceHistory(String fundaId) {
        return client.getPriceHistory(fundaId);
    }

    public Optional<RawListing> getListing(String fundaId) {
        return client.getListing(fundaId);
    }
}

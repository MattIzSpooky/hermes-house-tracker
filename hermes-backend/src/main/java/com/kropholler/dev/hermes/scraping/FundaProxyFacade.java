package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.FundaProxyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FundaProxyFacade {

    private final FundaProxyClient client;

    public List<RawPriceChange> getPriceHistory(String fundaId) {
        return client.getPriceHistory(fundaId);
    }
}

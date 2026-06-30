package com.kropholler.dev.hermes.listing.pricehistory;

import com.kropholler.dev.hermes.funda.FundaClient;
import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.funda.RawPriceChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;
    private final FundaClient fundaClient;
    private final JmsTemplate jmsTemplate;

    public void refreshAll() {
        int page = 0;
        Page<Listing> batch;
        do {
            batch = listingRepository.findAllByDeletedAtIsNull(PageRequest.of(page, 100));
            for (Listing listing : batch.getContent()) {
                try {
                    jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
                        new FetchPriceHistoryCommand(listing.getId(), listing.getFundaId()));
                } catch (Exception e) {
                    log.warn("Failed to enqueue price history fetch for listing {}: {}",
                        listing.getId(), e.getMessage());
                }
            }
            page++;
        } while (batch.hasNext());
    }

    // @Transactional begins a logical transaction but Hibernate 6 DELAYED_ACQUISITION_AND_HOLD
    // defers physical JDBC connection acquisition until the first SQL statement, so no connection
    // is held during the proxyFacade HTTP call below.
    @Transactional
    public void fetchAndStore(UUID listingId, String fundaId) {
        List<RawPriceChange> changes = fundaClient.getPriceHistory(fundaId);
        for (RawPriceChange change : changes) {
            if (change.timestamp() == null) continue;
            if (priceHistoryRepository.existsByListingIdAndTimestamp(listingId, change.timestamp())) {
                continue;
            }
            PriceHistoryEntry entry = new PriceHistoryEntry();
            entry.setListingId(listingId);
            entry.setPrice(change.price());
            entry.setStatus(change.status());
            entry.setSource(change.source());
            entry.setDate(change.date());
            entry.setTimestamp(change.timestamp());
            priceHistoryRepository.save(entry);
        }
    }
}

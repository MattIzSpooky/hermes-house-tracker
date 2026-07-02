package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingGeocodingBackfillService {

    private final ListingRepository listingRepository;
    private final JmsTemplate jmsTemplate;

    @Transactional(readOnly = true)
    public int queueMissingGeocoding() {
        List<UUID> ids = listingRepository.findIdsMissingLocation().stream()
                .map(UUID::fromString)
                .toList();
        for (UUID id : ids) {
            jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id));
        }
        log.info("Queued {} listing(s) for geocoding backfill", ids.size());
        return ids.size();
    }
}

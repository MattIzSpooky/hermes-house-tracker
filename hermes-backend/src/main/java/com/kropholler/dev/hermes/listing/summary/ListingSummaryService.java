package com.kropholler.dev.hermes.listing.summary;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSummaryService {

    private final ListingSummaryRepository repository;
    private final JmsTemplate jmsTemplate;

    @Transactional(readOnly = true)
    public Optional<ListingSummaryDto> findByListingId(UUID listingId) {
        Optional<ListingSummaryDto> dto = repository.findByListingId(listingId)
            .map(s -> new ListingSummaryDto(s.getListingId(), s.getSummary(), s.getGeneratedAt()));
        log.debug("findByListingId({}) found={}", listingId, dto.isPresent());
        return dto;
    }

    public void requestGeneration(UUID listingId) {
        log.info("Requesting summary generation for listing {}", listingId);
        jmsTemplate.convertAndSend(JmsQueues.LISTING_SUMMARY_GENERATE, listingId.toString());
    }
}

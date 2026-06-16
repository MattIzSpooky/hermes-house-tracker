package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ListingSummaryRepository;
import com.kropholler.dev.hermes.listing.internal.JmsQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingSummaryService {

    private final ListingSummaryRepository repository;
    private final JmsTemplate jmsTemplate;

    @Transactional(readOnly = true)
    public Optional<ListingSummaryDto> findByListingId(UUID listingId) {
        return repository.findByListingId(listingId)
            .map(s -> new ListingSummaryDto(s.getListingId(), s.getSummary(), s.getGeneratedAt()));
    }

    public void requestGeneration(UUID listingId) {
        jmsTemplate.convertAndSend(JmsQueues.LISTING_SUMMARY_GENERATE, listingId.toString());
    }
}

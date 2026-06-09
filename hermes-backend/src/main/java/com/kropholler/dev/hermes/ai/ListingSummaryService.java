package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ListingSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingSummaryService {

    private final ListingSummaryRepository repository;

    @Transactional(readOnly = true)
    public Optional<ListingSummaryDto> findByListingId(UUID listingId) {
        return repository.findByListingId(listingId)
            .map(s -> new ListingSummaryDto(s.getListingId(), s.getSummary(), s.getGeneratedAt()));
    }
}

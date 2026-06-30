package com.kropholler.dev.hermes.listing.summary;

import com.kropholler.dev.hermes.listing.summary.ListingSummary;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryRepository;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSummaryServiceTest {

    @Mock private ListingSummaryRepository repository;

    @InjectMocks
    private ListingSummaryService service;

    @Test
    void findByListingId_returnsDtoWhenSummaryExists() {
        UUID listingId = UUID.randomUUID();
        ListingSummary summary = new ListingSummary();
        summary.setListingId(listingId);
        summary.setSummary("A lovely apartment in Amsterdam.");
        summary.setGeneratedAt(Instant.now());

        when(repository.findByListingId(listingId)).thenReturn(Optional.of(summary));

        Optional<ListingSummaryDto> result = service.findByListingId(listingId);

        assertThat(result).isPresent();
        assertThat(result.get().summary()).isEqualTo("A lovely apartment in Amsterdam.");
    }

    @Test
    void findByListingId_returnsEmptyWhenNotFound() {
        UUID listingId = UUID.randomUUID();
        when(repository.findByListingId(listingId)).thenReturn(Optional.empty());

        assertThat(service.findByListingId(listingId)).isEmpty();
    }
}

package com.kropholler.dev.hermes.listing.summary;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryEntity;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryRepository;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSummaryServiceTest {

    @Mock private ListingSummaryRepository repository;
    @Mock private JmsTemplate jmsTemplate;

    @InjectMocks
    private ListingSummaryService service;

    @Test
    void findByListingId_returnsDtoWhenSummaryExists() {
        UUID listingId = UUID.randomUUID();
        ListingSummaryEntity summary = new ListingSummaryEntity();
        summary.setListingId(listingId);
        summary.setSummary("A lovely apartment in Amsterdam.");
        summary.setGeneratedAt(Instant.now());

        when(repository.findByListingId(listingId)).thenReturn(Optional.of(summary));

        assertThat(service.findByListingId(listingId).summary()).isEqualTo("A lovely apartment in Amsterdam.");
    }

    @Test
    void findByListingId_throwsNotFoundExceptionWhenNotFound() {
        UUID listingId = UUID.randomUUID();
        when(repository.findByListingId(listingId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findByListingId(listingId))
            .isInstanceOf(com.kropholler.dev.hermes.exception.NotFoundException.class)
            .hasMessageContaining("No summary available for listing " + listingId);
    }

    @Test
    void requestGeneration_sendsMessageToQueue() {
        UUID listingId = UUID.randomUUID();

        service.requestGeneration(listingId);

        verify(jmsTemplate).convertAndSend(JmsQueues.LISTING_SUMMARY_GENERATE, listingId.toString());
    }
}

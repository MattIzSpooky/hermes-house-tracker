package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingGeocodingBackfillServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private JmsTemplate jmsTemplate;

    @InjectMocks
    private ListingGeocodingBackfillService service;

    @Test
    void queueMissingGeocoding_sendsOneCommandPerMissingListing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(listingRepository.findIdsMissingLocation()).thenReturn(List.of(id1.toString(), id2.toString()));

        int result = service.queueMissingGeocoding();

        assertThat(result).isEqualTo(2);
        verify(jmsTemplate).convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id1));
        verify(jmsTemplate).convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id2));
    }

    @Test
    void queueMissingGeocoding_noMissingListings_returnsZeroAndSendsNothing() {
        when(listingRepository.findIdsMissingLocation()).thenReturn(List.of());

        int result = service.queueMissingGeocoding();

        assertThat(result).isZero();
        verify(jmsTemplate, org.mockito.Mockito.never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }
}

package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceSearchTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private ListingMapper mapper;
    @Mock private GeocodingService geocodingService;

    @InjectMocks
    private ListingService service;

    @Test
    void findAll_withNonEmptyParams_usesSpecificationPath() {
        var params = new ListingSearchParams("Teststraat", null, null, null, null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(any(Specification.class), eq(pageable));
        verify(listingRepository, never()).findAll(eq(pageable));
    }

    @Test
    void findAll_withEmptyParams_usesSimplePath() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(eq(pageable));
        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listingSearchParams_isEmpty_trueWhenAllBlank() {
        var params = new ListingSearchParams("", " ", null, "", null, null, null, null, null, null, null, null);
        assertThat(params.isEmpty()).isTrue();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenAnyNonBlank() {
        var params = new ListingSearchParams(null, null, null, "1234AB", null, null, null, null, null, null, null, null);
        assertThat(params.isEmpty()).isFalse();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenMinBedroomsSet() {
        var params = new ListingSearchParams(null, null, null, null, null, 2, null, null, null, null, null, null);
        assertThat(params.isEmpty()).isFalse();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenMinLivingAreaSet() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, 80, null, null, null, null);
        assertThat(params.isEmpty()).isFalse();
    }
}

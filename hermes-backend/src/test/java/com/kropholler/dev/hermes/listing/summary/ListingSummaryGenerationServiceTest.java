package com.kropholler.dev.hermes.listing.summary;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSummaryGenerationServiceTest {

    @Mock ListingSummaryRepository summaryRepository;
    @Mock ListingService listingService;
    @Mock ChatClient.Builder chatClientBuilder;
    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;

    @InjectMocks
    ListingSummaryGenerationService service;

    private void stubLlm(String response) {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }

    @Test
    void generate_listingFoundWithAllFields_savesNewSummary() {
        UUID id = UUID.randomUUID();
        ListingDto listing = new ListingDto(id, "fnd1", "http://x.com",
            "Kerkstraat", "12", "A", "1234AB", "Amsterdam", "Noord-Holland",
            Instant.now(), Instant.now(), 450000, ListingStatus.FOR_SALE,
            "Beautiful house.", 120, 5, 3, "A", 200, null);
        // entry with non-null price covers L77 true branch
        PriceHistoryEntryDto entry = new PriceHistoryEntryDto(
            UUID.randomUUID(), 450000, "asking_price", null, LocalDate.now(), Instant.now());

        when(listingService.findById(id)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of(entry));
        when(summaryRepository.findByListingId(id)).thenReturn(Optional.empty());
        stubLlm("AI-generated summary.");

        service.generate(id);

        ArgumentCaptor<ListingSummaryEntity> captor = ArgumentCaptor.forClass(ListingSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getSummary()).isEqualTo("AI-generated summary.");
    }

    @Test
    void generate_listingFoundWithSparseFields_updatesExistingSummary() {
        UUID id = UUID.randomUUID();
        // null optionals cover false branches of L57, L63, L67-71; blank description covers L82 second false
        ListingDto listing = new ListingDto(id, "fnd2", "http://x.com",
            "Straat", "1", null, "5678CD", "Utrecht", "Utrecht",
            Instant.now(), Instant.now(), null, null, "   ", null, null, null, null, null, null);
        // empty price history covers L73 false branch
        when(listingService.findById(id)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of());

        ListingSummaryEntity existing = new ListingSummaryEntity();
        existing.setListingId(id);
        existing.setSummary("old summary");
        when(summaryRepository.findByListingId(id)).thenReturn(Optional.of(existing));
        stubLlm("Updated summary.");

        service.generate(id);

        verify(summaryRepository).save(existing);
        assertThat(existing.getSummary()).isEqualTo("Updated summary.");
    }

    @Test
    void generate_listingNotFound_skipsLlmAndSave() {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        service.generate(id);

        verify(chatClientBuilder, never()).build();
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void generate_llmThrows_savesFallbackMessage() {
        UUID id = UUID.randomUUID();
        // description null covers L82 first false (description == null)
        ListingDto listing = new ListingDto(id, "fnd3", "http://x.com",
            "Laan", "99", null, "9999AA", "Rotterdam", "Zuid-Holland",
            Instant.now(), Instant.now(), 300000, ListingStatus.FOR_SALE,
            null, 80, 4, 2, "B", null, null);

        when(listingService.findById(id)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of());
        when(summaryRepository.findByListingId(id)).thenReturn(Optional.empty());
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenThrow(new RuntimeException("LLM timeout"));

        service.generate(id);

        ArgumentCaptor<ListingSummaryEntity> captor = ArgumentCaptor.forClass(ListingSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getSummary()).isEqualTo("Summary could not be generated.");
    }

    @Test
    void generate_priceHistoryEntryWithNullPrice_formatsWithQuestionMark() {
        UUID id = UUID.randomUUID();
        ListingDto listing = new ListingDto(id, "fnd4", "http://x.com",
            "Dorpstraat", "3", null, "1111BB", "Leiden", "Zuid-Holland",
            Instant.now(), Instant.now(), 250000, ListingStatus.FOR_SALE,
            null, 70, 3, 2, "C", null, null);
        // null price entry covers L77 false branch ("?")
        PriceHistoryEntryDto nullPriceEntry = new PriceHistoryEntryDto(
            UUID.randomUUID(), null, "asking_price", null, LocalDate.now(), Instant.now());

        when(listingService.findById(id)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(id)).thenReturn(List.of(nullPriceEntry));
        when(summaryRepository.findByListingId(id)).thenReturn(Optional.empty());
        stubLlm("Summary for null price listing.");

        service.generate(id);

        verify(summaryRepository).save(any());
    }
}

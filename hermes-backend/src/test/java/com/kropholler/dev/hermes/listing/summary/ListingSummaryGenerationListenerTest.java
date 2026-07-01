package com.kropholler.dev.hermes.listing.summary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ListingSummaryGenerationListenerTest {

    @Mock ListingSummaryGenerationService generationService;
    @InjectMocks ListingSummaryGenerationListener listener;

    @Test
    void onMessage_validUuid_delegatesToGenerationService() {
        UUID id = UUID.randomUUID();

        listener.onMessage(id.toString());

        verify(generationService).generate(id);
    }

    @Test
    void onMessage_invalidUuid_skipsGenerationAndReturns() {
        listener.onMessage("not-a-valid-uuid");

        verifyNoInteractions(generationService);
    }

    @Test
    void onMessage_generationServiceThrows_rethrowsException() {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("generation failed")).when(generationService).generate(id);

        assertThatThrownBy(() -> listener.onMessage(id.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("generation failed");
    }
}

package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock FavouriteService favouriteService;

    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    ObjectMapper objectMapper = new ObjectMapper();
    ResearchTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResearchTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favouriteService, meterRegistry, objectMapper);
    }

    @Test
    void returnsNotificationWithAiResponse() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Analysis complete: property A is the best deal.");

        AgentTask task = researchTask("analyse my favourites");

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().body()).contains("Analysis complete");
        assertThat(result.get().listingIds()).isEmpty();
    }

    @Test
    void returnsEmptyWhenAiReturnsBlank() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("  ");

        Optional<NotificationContent> result = handler.handle(researchTask("analyse"));

        assertThat(result).isEmpty();
    }

    private AgentTask researchTask(String prompt) throws Exception {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.RESEARCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setClientId(UUID.randomUUID());
        task.setName("Research: " + prompt);
        task.setPayload(objectMapper.writeValueAsString(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        return task;
    }
}

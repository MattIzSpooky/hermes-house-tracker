package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.ResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
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
    @Mock
    FavoriteService favoriteService;

    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    ObjectMapper objectMapper = new ObjectMapper();
    ResearchTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResearchTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favoriteService, meterRegistry, objectMapper);
    }

    @Test
    void returnsNotificationWithAiResponse() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Analysis complete: property A is the best deal.");

        AgentTaskEntity task = researchTask("analyse my favourites");

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

    @Test
    void handle_aiReturnsNull_returnsEmpty() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(null);

        Optional<NotificationContent> result = handler.handle(researchTask("analyse my favourites"));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_invalidPayload_returnsEmpty() throws Exception {
        AgentTaskEntity task = researchTask("analyse");
        task.setPayload("{not valid json");

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    private AgentTaskEntity researchTask(String prompt) throws Exception {
        AgentTaskEntity task = new AgentTaskEntity();
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

package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigestTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock FavouriteService favouriteService;

    ObjectMapper objectMapper = new ObjectMapper();
    DigestTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DigestTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favouriteService, new SimpleMeterRegistry(), objectMapper);
    }

    @Test
    void returnsDigestNotification() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("This week in Amsterdam: 3 new listings appeared...");

        AgentTask task = digestTask(List.of("Amsterdam", "Utrecht"));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("Weekly digest");
        assertThat(result.get().body()).contains("Amsterdam");
    }

    private AgentTask digestTask(List<String> cities) throws Exception {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.DIGEST);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setClientId(UUID.randomUUID());
        task.setName("Weekly digest");
        task.setPayload(objectMapper.writeValueAsString(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(Instant.now());
        return task;
    }
}

package com.kropholler.dev.hermes.ai.agent.task.handler;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.favorites.FavoriteService;
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
    @Mock
    FavoriteService favoriteService;

    ObjectMapper objectMapper = new JsonMapper();
    DigestTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DigestTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favoriteService, new SimpleMeterRegistry(), objectMapper);
    }

    @Test
    void returnsDigestNotification() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("This week in Amsterdam: 3 new listings appeared...");

        AgentTaskEntity task = digestTask(List.of("Amsterdam", "Utrecht"));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("Weekly digest");
        assertThat(result.get().body()).contains("Amsterdam");
    }

    @Test
    void handle_aiReturnsNull_returnsEmpty() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(null);

        Optional<NotificationContent> result = handler.handle(digestTask(List.of("Amsterdam")));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_aiReturnsBlank_returnsEmpty() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("   ");

        Optional<NotificationContent> result = handler.handle(digestTask(List.of("Amsterdam")));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_invalidPayload_returnsEmpty() throws Exception {
        AgentTaskEntity task = digestTask(List.of("Amsterdam"));
        task.setPayload("{not valid json");

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    private AgentTaskEntity digestTask(List<String> cities) throws Exception {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.DIGEST);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setUserId(UUID.randomUUID());
        task.setName("Weekly digest");
        task.setPayload(objectMapper.writeValueAsString(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(Instant.now());
        return task;
    }
}

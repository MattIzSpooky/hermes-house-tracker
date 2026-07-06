package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaResearchTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock UserProfileRepository userProfileRepository;

    JsonMapper objectMapper = new JsonMapper();
    AreaResearchTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AreaResearchTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, new SimpleMeterRegistry(), objectMapper, userProfileRepository);
    }

    private AgentTaskEntity task(UUID userId, AreaResearchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setUserId(userId);
        task.setName("Best nearby homes");
        task.setPayload(objectMapper.writeValueAsString(payload));
        task.setNextRunAt(Instant.now());
        return task;
    }

    private ListingDto listing(UUID id) {
        return new ListingDto(id, "fundaId", "http://example.com", "Herenstraat", "10", null,
            "3500AA", "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            350000, ListingStatus.FOR_SALE, null, 120, 5, 3, null, null, null);
    }

    private void stubAiNarrative(String narrative) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(narrative);
    }

    @Test
    void handle_homeAddress_usesProfileCoordinatesAndReturnsNotification() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(15, 5, 3, null, 80, null, 500000, null, null, null);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        UUID listingId = UUID.randomUUID();
        when(listingService.findNearLocation(4.9041, 52.3676, 3, null, 80, null, null, null, 500000, 15_000, 5))
            .thenReturn(List.of(listing(listingId)));
        stubAiNarrative("1. Herenstraat 10 is the best match because it fits the budget and size.");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 best listings within 15km");
        assertThat(result.get().body()).contains("Herenstraat 10");
        assertThat(result.get().listingIds()).containsExactly(listingId);
    }

    @Test
    void handle_overrideCoordinates_usedInsteadOfProfile() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative("Good options near the overridden location.");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isPresent();
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void handle_noOverrideAndNoProfileCoordinates_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, null, null);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_profileExistsButCoordinatesNull_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, null, null);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_noCandidatesFound_returnsEmptyWithoutCallingAi() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of());

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_aiReturnsBlank_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative("   ");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_aiReturnsNull_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative(null);

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_invalidPayload_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setUserId(userId);
        task.setName("Broken");
        task.setPayload("{not valid json");
        task.setNextRunAt(Instant.now());

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
        verifyNoInteractions(userProfileRepository, chatClient);
    }

    @Test
    void getType_returnsAreaResearch() {
        assertThat(handler.getType()).isEqualTo(AgentTaskType.AREA_RESEARCH);
    }
}

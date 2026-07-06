package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveAreaResearchToolTest {

    @Mock AgentTaskService agentTaskService;
    @Mock UserProfileRepository userProfileRepository;
    @Mock GeocodingService geocodingService;

    private SaveAreaResearchTool tool(UUID userId) {
        return new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService);
    }

    @Test
    void noOverrideAndProfileHasAddress_createsTask() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "Best listings within 15km", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 15, 10, 3, null, 80, null, 500000, null, null, null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().radiusKm()).isEqualTo(15);
        assertThat(cap.getValue().limit()).isEqualTo(10);
        assertThat(cap.getValue().overrideLon()).isNull();
        assertThat(cap.getValue().overrideLat()).isNull();
        assertThat(result).contains("saved");
        verifyNoInteractions(geocodingService);
    }

    @Test
    void noOverrideAndNoProfileAddress_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        String result = tool(userId).saveAreaResearch(null, 15, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("set your home address");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void profileExistsButCoordinatesNull_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        String result = tool(userId).saveAreaResearch(null, 15, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("set your home address");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void nearAddressOverride_geocodesAndFreezesCoordinates() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("10, Kerkstraat, Utrecht", "", ""))
            .thenReturn(Optional.of(new GeocodeResult(5.1214, 52.0907, null)));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "name", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            "10, Kerkstraat, Utrecht", null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().overrideLon()).isEqualTo(5.1214);
        assertThat(cap.getValue().overrideLat()).isEqualTo(52.0907);
        assertThat(result).contains("saved");
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void nearCityOverride_geocodesAndFreezesCoordinates() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeCity("Rotterdam"))
            .thenReturn(Optional.of(new GeocodeResult(4.4777, 51.9244, null)));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "name", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            null, "Rotterdam");

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().overrideLon()).isEqualTo(4.4777);
        assertThat(cap.getValue().overrideLat()).isEqualTo(51.9244);
        assertThat(result).contains("saved");
    }

    @Test
    void overrideGeocodingFails_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("Nowhere Street", "", "")).thenReturn(Optional.empty());

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            "Nowhere Street", null);

        assertThat(result).contains("could not find", "Could not find");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void blankName_buildsDefaultNameFromRadius() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(null);

        tool(userId).saveAreaResearch("  ", 12, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentTaskService).createAreaResearch(eq(userId), nameCaptor.capture(), any());
        assertThat(nameCaptor.getValue()).contains("12");
    }

    @Test
    void blankKeywords_treatedAsNullInPayload() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(null);

        tool(userId).saveAreaResearch("My search", 12, null, null, null, null, null, null, "  ", null, null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), eq("My search"), cap.capture());
        assertThat(cap.getValue().keywords()).isNull();
    }
}

package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import com.kropholler.dev.hermes.security.NoOpUserProfileSyncFilterTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean
    NotificationService notificationService;
    @MockitoBean
    NotificationApiMapper notificationApiMapper;

    @Test
    void getNotifications_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(notifId, taskId, userId,
            "New listing found", "Check it out", List.of(listingId),
            false, Instant.parse("2026-06-19T08:00:00Z"), null);

        NotificationResponse response = new NotificationResponse();
        response.setId(notifId);
        response.setTaskId(taskId);
        response.setUserId(userId);
        response.setTitle("New listing found");
        response.setBody("Check it out");
        response.setListingIds(List.of(listingId));
        response.setRead(false);

        when(notificationService.findByUserId(userId)).thenReturn(List.of(dto));
        when(notificationApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/notifications")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(notifId.toString()))
            .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
            .andExpect(jsonPath("$[0].userId").value(userId.toString()))
            .andExpect(jsonPath("$[0].title").value("New listing found"))
            .andExpect(jsonPath("$[0].body").value("Check it out"))
            .andExpect(jsonPath("$[0].listingIds[0]").value(listingId.toString()))
            .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void getUnreadCount_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.countUnread(userId)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(5));

        verify(notificationService).countUnread(eq(userId));
    }

    @Test
    void markNotificationRead_usesSubjectFromJwt() throws Exception {
        UUID notifId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(eq(notifId), eq(callerId));
    }
}

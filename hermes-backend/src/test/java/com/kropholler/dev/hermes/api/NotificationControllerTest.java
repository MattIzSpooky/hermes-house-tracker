package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.agent.NotificationDto;
import com.kropholler.dev.hermes.agent.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService notificationService;

    @Test
    void getNotifications_returnsMappedList() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(notifId, taskId, clientId,
            "New listing found", "Check it out", List.of(listingId),
            false, Instant.parse("2026-06-19T08:00:00Z"), null);

        when(notificationService.findByClientId(clientId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notifications").param("clientId", clientId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(notifId.toString()))
            .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
            .andExpect(jsonPath("$[0].clientId").value(clientId.toString()))
            .andExpect(jsonPath("$[0].title").value("New listing found"))
            .andExpect(jsonPath("$[0].body").value("Check it out"))
            .andExpect(jsonPath("$[0].listingIds[0]").value(listingId.toString()))
            .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void getUnreadCount_returnsCount() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(notificationService.countUnread(clientId)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count").param("clientId", clientId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void markNotificationRead_callsServiceAndReturns204() throws Exception {
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(notifId);
    }
}

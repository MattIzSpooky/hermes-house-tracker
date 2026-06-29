package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.agent.notification.NotificationDto;
import com.kropholler.dev.hermes.ai.agent.notification.NotificationService;
import com.kropholler.dev.hermes.api.generated.NotificationsApi;
import com.kropholler.dev.hermes.api.generated.model.NotificationResponse;
import com.kropholler.dev.hermes.api.generated.model.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class NotificationController implements NotificationsApi {

    private final NotificationService notificationService;

    @Override
    public ResponseEntity<List<NotificationResponse>> getNotifications(UUID clientId) {
        List<NotificationResponse> responses = notificationService.findByClientId(clientId)
            .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadCount(UUID clientId) {
        UnreadCountResponse r = new UnreadCountResponse();
        r.setCount(notificationService.countUnread(clientId));
        return ResponseEntity.ok(r);
    }

    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(NotificationDto dto) {
        NotificationResponse r = new NotificationResponse();
        r.setId(dto.id());
        r.setTaskId(dto.taskId());
        r.setClientId(dto.clientId());
        r.setTitle(dto.title());
        r.setBody(dto.body());
        r.setListingIds(dto.listingIds() != null ? dto.listingIds() : List.of());
        r.setRead(dto.read());
        r.setCreatedAt(dto.createdAt() != null ? dto.createdAt().atOffset(ZoneOffset.UTC) : null);
        r.setEmailSentAt(dto.emailSentAt() != null ? dto.emailSentAt().atOffset(ZoneOffset.UTC) : null);
        return r;
    }
}

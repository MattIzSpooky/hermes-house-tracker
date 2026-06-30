package com.kropholler.dev.hermes.ai.agent.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationsApi {

    private final NotificationService notificationService;
    private final NotificationApiMapper notificationApiMapper;

    @Override
    public ResponseEntity<List<NotificationResponse>> getNotifications(UUID clientId) {
        List<NotificationResponse> responses = notificationService.findByClientId(clientId)
            .stream().map(notificationApiMapper::toResponse).toList();
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
}

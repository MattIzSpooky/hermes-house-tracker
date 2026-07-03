package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import com.kropholler.dev.hermes.notification.openapi.NotificationsApi;
import com.kropholler.dev.hermes.notification.openapi.UnreadCountResponse;
import com.kropholler.dev.hermes.security.CurrentUser;
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
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        List<NotificationResponse> responses = notificationService.findByUserId(CurrentUser.current().id())
            .stream().map(notificationApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        UnreadCountResponse r = new UnreadCountResponse();
        r.setCount(notificationService.countUnread(CurrentUser.current().id()));
        return ResponseEntity.ok(r);
    }

    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}

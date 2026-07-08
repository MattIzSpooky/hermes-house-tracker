package com.kropholler.dev.hermes.notification;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messaging;
    private final EmailNotificationSender emailSender;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationDto save(UUID taskId, UUID userId, NotificationContent content) {
        log.info("Saving notification for task={}, user={}, title='{}'", taskId, userId, content.title());
        NotificationEntity notification = new NotificationEntity();
        notification.setTaskId(taskId);
        notification.setUserId(userId);
        notification.setTitle(content.title());
        notification.setBody(content.body());
        notification.setListingIds(serializeIds(content.listingIds()));
        NotificationEntity saved = notificationRepository.save(notification);

        NotificationDto dto = toDto(saved, content.listingIds());
        messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", dto);
        emailSender.sendAsync(dto);
        log.debug("Notification {} saved and dispatched (ws + email) for user {}", saved.getId(), userId);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findByUserId(UUID userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(n -> toDto(n, deserializeIds(n.getListingIds()))).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Notification " + notificationId + " not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to mark this notification read");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked read by user {}", notificationId, userId);
    }

    private String serializeIds(List<UUID> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JacksonException e) {
            log.warn("Failed to serialize listing ids {}: {}", ids, e.getMessage());
            return "[]";
        }
    }

    private List<UUID> deserializeIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (JacksonException e) {
            log.warn("Failed to deserialize listing ids from '{}': {}", json, e.getMessage());
            return List.of();
        }
    }

    private NotificationDto toDto(NotificationEntity n, List<UUID> listingIds) {
        return new NotificationDto(n.getId(), n.getTaskId(), n.getUserId(),
            n.getTitle(), n.getBody(), listingIds, n.isRead(),
            n.getCreatedAt(), n.getEmailSentAt());
    }
}

package com.kropholler.dev.hermes.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public NotificationDto save(UUID taskId, UUID clientId, NotificationContent content) {
        NotificationEntity notification = new NotificationEntity();
        notification.setTaskId(taskId);
        notification.setClientId(clientId);
        notification.setTitle(content.title());
        notification.setBody(content.body());
        notification.setListingIds(serializeIds(content.listingIds()));
        NotificationEntity saved = notificationRepository.save(notification);

        NotificationDto dto = toDto(saved, content.listingIds());
        messaging.convertAndSend("/topic/notifications/" + clientId, dto);
        emailSender.sendAsync(dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findByClientId(UUID clientId) {
        return notificationRepository.findTop50ByClientIdOrderByCreatedAtDesc(clientId)
            .stream().map(n -> toDto(n, deserializeIds(n.getListingIds()))).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID clientId) {
        return notificationRepository.countByClientIdAndReadFalse(clientId);
    }

    @Transactional
    public void markRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    private String serializeIds(List<UUID> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<UUID> deserializeIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private NotificationDto toDto(NotificationEntity n, List<UUID> listingIds) {
        return new NotificationDto(n.getId(), n.getTaskId(), n.getClientId(),
            n.getTitle(), n.getBody(), listingIds, n.isRead(),
            n.getCreatedAt(), n.getEmailSentAt());
    }
}

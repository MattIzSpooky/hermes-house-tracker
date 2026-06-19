package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.internal.Notification;
import com.kropholler.dev.hermes.agent.internal.NotificationContent;
import com.kropholler.dev.hermes.agent.internal.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messaging;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Value("${hermes.notifications.to-email}")
    private String toEmail;

    @Transactional
    public NotificationDto save(UUID taskId, UUID clientId, NotificationContent content) {
        Notification notification = new Notification();
        notification.setTaskId(taskId);
        notification.setClientId(clientId);
        notification.setTitle(content.title());
        notification.setBody(content.body());
        notification.setListingIds(serializeIds(content.listingIds()));
        Notification saved = notificationRepository.save(notification);

        NotificationDto dto = toDto(saved, content.listingIds());
        messaging.convertAndSend("/topic/notifications/" + clientId, dto);
        sendEmailAsync(dto);
        return dto;
    }

    @Async
    protected void sendEmailAsync(NotificationDto dto) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("[Hermes] " + dto.title());
            msg.setText(dto.body());
            mailSender.send(msg);
            notificationRepository.findById(dto.id()).ifPresent(n -> {
                n.setEmailSentAt(Instant.now());
                notificationRepository.save(n);
            });
        } catch (Exception e) {
            log.error("Failed to send notification email for {}", dto.id(), e);
        }
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

    private NotificationDto toDto(Notification n, List<UUID> listingIds) {
        return new NotificationDto(n.getId(), n.getTaskId(), n.getClientId(),
            n.getTitle(), n.getBody(), listingIds, n.isRead(),
            n.getCreatedAt(), n.getEmailSentAt());
    }
}

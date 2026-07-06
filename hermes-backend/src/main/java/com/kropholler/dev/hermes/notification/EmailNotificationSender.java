package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Async
    public void sendAsync(NotificationDto dto) {
        Optional<String> recipient = resolveRecipient(dto.userId());
        if (recipient.isEmpty()) {
            log.warn("Skipping notification email for {}: no email on file for user {}", dto.id(), dto.userId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(recipient.get());
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

    private Optional<String> resolveRecipient(UUID userId) {
        return userProfileRepository.findById(userId)
            .map(UserProfileEntity::getEmail)
            .filter(email -> email != null && !email.isBlank());
    }
}

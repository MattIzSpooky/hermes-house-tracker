package com.kropholler.dev.hermes.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Value("${hermes.notifications.to-email}")
    private String toEmail;

    @Async
    public void sendAsync(NotificationDto dto) {
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
}

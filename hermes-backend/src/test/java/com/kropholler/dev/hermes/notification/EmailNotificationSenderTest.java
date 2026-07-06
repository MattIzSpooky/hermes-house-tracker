package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock JavaMailSender mailSender;
    @Mock NotificationRepository notificationRepository;
    @Mock UserProfileRepository userProfileRepository;
    @InjectMocks EmailNotificationSender sender;

    private void setEmails(String from, String to) {
        ReflectionTestUtils.setField(sender, "fromEmail", from);
        ReflectionTestUtils.setField(sender, "toEmail", to);
    }

    private NotificationDto dto(UUID id) {
        return new NotificationDto(id, null, UUID.randomUUID(),
            "Price alert", "Dropped 10%", List.of(), false, null, null);
    }

    @Test
    void sendAsync_sendsMailWithCorrectFields() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("from@hermes.nl");
        assertThat(msg.getTo()).containsExactly("to@user.nl");
        assertThat(msg.getSubject()).isEqualTo("[Hermes] Price alert");
        assertThat(msg.getText()).isEqualTo("Dropped 10%");
    }

    @Test
    void sendAsync_updatesEmailSentAt() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailSentAt()).isNotNull();
    }

    @Test
    void sendAsync_swallowsMailException() {
        UUID id = UUID.randomUUID();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.sendAsync(dto(id));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendAsync_userHasProfileEmail_sendsToProfileEmail() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        setEmails("from@hermes.nl", "fallback@hermes.nl");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setEmail("actualuser@hermes.local");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(new NotificationEntity()));

        sender.sendAsync(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("actualuser@hermes.local");
    }

    @Test
    void sendAsync_userHasNoProfileEmail_fallsBackToConfigValue() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        setEmails("from@hermes.nl", "fallback@hermes.nl");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(new NotificationEntity()));

        sender.sendAsync(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("fallback@hermes.nl");
    }
}

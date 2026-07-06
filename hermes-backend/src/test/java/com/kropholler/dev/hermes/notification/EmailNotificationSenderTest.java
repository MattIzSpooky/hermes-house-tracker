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

    private void setEmail(String from) {
        ReflectionTestUtils.setField(sender, "fromEmail", from);
    }

    private NotificationDto dto(UUID id) {
        return new NotificationDto(id, null, UUID.randomUUID(),
            "Price alert", "Dropped 10%", List.of(), false, null, null);
    }

    private UserProfileEntity profileWithEmail(String email) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(UUID.randomUUID());
        profile.setEmail(email);
        return profile;
    }

    @Test
    void sendAsync_sendsMailWithCorrectFields() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
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
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailSentAt()).isNotNull();
    }

    @Test
    void sendAsync_swallowsMailException() {
        UUID id = UUID.randomUUID();
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
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
        setEmail("from@hermes.nl");
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
    void sendAsync_userHasNoProfileEmail_skipsSendingAndDoesNotThrow() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        sender.sendAsync(dto);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }
}

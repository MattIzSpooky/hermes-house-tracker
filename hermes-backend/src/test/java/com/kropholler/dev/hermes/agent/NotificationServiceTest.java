package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.notification.EmailNotificationSender;
import com.kropholler.dev.hermes.ai.agent.notification.NotificationDto;
import com.kropholler.dev.hermes.ai.agent.notification.NotificationService;
import com.kropholler.dev.hermes.ai.agent.task.data.Notification;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.data.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repo;
    @Mock SimpMessagingTemplate messaging;
    @Mock
    EmailNotificationSender emailSender;
    @Spy ObjectMapper objectMapper;
    @InjectMocks
    NotificationService service;

    @Test
    void savePersistsAndPushesOverWebSocket() {
        UUID clientId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        NotificationContent content = new NotificationContent("title", "body", List.of());

        service.save(taskId, clientId, content);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("title");

        verify(messaging).convertAndSend(
            eq("/topic/notifications/" + clientId), any(NotificationDto.class));
    }
}

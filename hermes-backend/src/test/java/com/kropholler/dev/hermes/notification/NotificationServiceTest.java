package com.kropholler.dev.hermes.notification;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repo;
    @Mock SimpMessagingTemplate messaging;
    @Mock
    EmailNotificationSender emailSender;
    @Spy ObjectMapper objectMapper = new JsonMapper();
    @InjectMocks
    NotificationService service;

    @Test
    void savePersistsAndPushesToUserQueue() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        NotificationContent content = new NotificationContent("title", "body", List.of());

        service.save(taskId, userId, content);

        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("title");

        verify(messaging).convertAndSendToUser(
            eq(userId.toString()), eq("/queue/notifications"), any(NotificationDto.class));
    }

    @Test
    void save_whenSerializeThrows_usesFallbackEmptyJson() throws Exception {
        // Covers serializeIds catch block (L63-64)
        doThrow(new JacksonException("forced") {}).when(objectMapper).writeValueAsString(any());
        UUID userId = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.save(UUID.randomUUID(), userId, new NotificationContent("t", "b", List.of()));

        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getListingIds()).isEqualTo("[]");
    }

    @Test
    void findByUserId_withValidListingIds_returnsParsedDtos() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("[\"" + listingId + "\"]");
        when(repo.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).listingIds()).containsExactly(listingId);
    }

    @Test
    void findByUserId_withNullListingIds_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds(null);
        when(repo.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByUserId(userId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void findByUserId_withBlankListingIds_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("   ");
        when(repo.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByUserId(userId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void findByUserId_withInvalidJson_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("not-valid-json");
        when(repo.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByUserId(userId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void countUnread_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repo.countByUserIdAndReadFalse(userId)).thenReturn(3L);

        assertThat(service.countUnread(userId)).isEqualTo(3L);
    }

    @Test
    void markRead_ownerMarksReadSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        entity.setUserId(userId);
        when(repo.findById(notificationId)).thenReturn(Optional.of(entity));

        service.markRead(notificationId, userId);

        assertThat(entity.isRead()).isTrue();
        verify(repo).save(entity);
    }

    @Test
    void markRead_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(repo.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId, userId))
            .isInstanceOf(com.kropholler.dev.hermes.exception.NotFoundException.class)
            .hasMessageContaining("Notification " + notificationId + " not found");

        verify(repo, never()).save(any());
    }

    @Test
    void markRead_throws403WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        entity.setUserId(ownerId);
        when(repo.findById(notificationId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.markRead(notificationId, callerId))
            .isInstanceOf(com.kropholler.dev.hermes.exception.ForbiddenException.class);

        verify(repo, never()).save(any());
    }
}

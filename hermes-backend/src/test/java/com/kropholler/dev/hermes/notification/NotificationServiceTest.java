package com.kropholler.dev.hermes.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            NotificationEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        NotificationContent content = new NotificationContent("title", "body", List.of());

        service.save(taskId, clientId, content);

        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("title");

        verify(messaging).convertAndSend(
            eq("/topic/notifications/" + clientId), any(NotificationDto.class));
    }

    @Test
    void save_whenSerializeThrows_usesFallbackEmptyJson() throws Exception {
        // Covers serializeIds catch block (L63-64)
        doThrow(new JsonProcessingException("forced") {}).when(objectMapper).writeValueAsString(any());
        UUID clientId = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> {
            NotificationEntity n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.save(UUID.randomUUID(), clientId, new NotificationContent("t", "b", List.of()));

        ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getListingIds()).isEqualTo("[]");
    }

    @Test
    void findByClientId_withValidListingIds_returnsParsedDtos() {
        // Covers findByClientId (L43-44), deserializeIds non-null non-blank path (L69 false+false, L71)
        UUID clientId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(clientId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("[\"" + listingId + "\"]");
        when(repo.findTop50ByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByClientId(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).listingIds()).containsExactly(listingId);
    }

    @Test
    void findByClientId_withNullListingIds_returnsEmptyList() {
        // Covers deserializeIds L69 first false branch: json == null
        UUID clientId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(clientId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds(null);
        when(repo.findTop50ByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByClientId(clientId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void findByClientId_withBlankListingIds_returnsEmptyList() {
        // Covers deserializeIds L69 second false branch: json.isBlank()
        UUID clientId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(clientId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("   ");
        when(repo.findTop50ByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByClientId(clientId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void findByClientId_withInvalidJson_returnsEmptyList() {
        // Covers deserializeIds catch block (L72-73)
        UUID clientId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(clientId);
        entity.setTitle("t");
        entity.setBody("b");
        entity.setListingIds("not-valid-json");
        when(repo.findTop50ByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(entity));

        List<NotificationDto> result = service.findByClientId(clientId);

        assertThat(result.get(0).listingIds()).isEmpty();
    }

    @Test
    void countUnread_delegatesToRepository() {
        UUID clientId = UUID.randomUUID();
        when(repo.countByClientIdAndReadFalse(clientId)).thenReturn(3L);

        assertThat(service.countUnread(clientId)).isEqualTo(3L);
    }

    @Test
    void markRead_whenFound_setsReadAndSaves() {
        // Covers markRead ifPresent true branch (L55-56)
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        when(repo.findById(notificationId)).thenReturn(Optional.of(entity));

        service.markRead(notificationId);

        assertThat(entity.isRead()).isTrue();
        verify(repo).save(entity);
    }

    @Test
    void markRead_whenNotFound_doesNothing() {
        // Covers markRead ifPresent false branch
        UUID notificationId = UUID.randomUUID();
        when(repo.findById(notificationId)).thenReturn(Optional.empty());

        service.markRead(notificationId);

        verify(repo, never()).save(any());
    }
}

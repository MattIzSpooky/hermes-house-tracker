# ClientId → JWT Identity Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the browser-generated `clientId` UUID (favorites, notifications, agent tasks, chat) with identity derived server-side from the authenticated JWT, and give the `/ws/chat` STOMP WebSocket real authentication for the first time.

**Architecture:** Three tables (`favorites`, `notifications`, `agent_tasks`) get their `client_id` column renamed to `user_id`; `chat_messages` gains a new `user_id` column alongside its existing `session_id`. A new `ChannelInterceptor` validates a bearer token on STOMP `CONNECT` and attaches a JWT-backed `Principal` to the WebSocket session. Every backend module drops `clientId` from its request surface and derives identity via `CurrentUser` (HTTP) or the STOMP principal (chat). Notifications switch from a manually UUID-keyed topic to Spring's `/user/queue` convention. All tables are truncated as part of the migration since pre-launch anonymous data isn't worth preserving — this lets `user_id` be `NOT NULL` everywhere.

**Tech Stack:** Spring Boot 4.0.6, Spring Security OAuth2 Resource Server, Spring WebSocket/STOMP messaging, Flyway, MapStruct, Angular 22, `@stomp/stompjs`, `keycloak-js`.

## Global Constraints

- Identity always comes from the authenticated JWT (`CurrentUser.current()` for HTTP, the STOMP `Principal` for chat) — never from a request body, query param, or path variable. No endpoint may accept a user id as client input.
- `chat_messages.session_id` is untouched in meaning — it still identifies one conversation thread, not identity. Only `user_id` (new) carries ownership.
- Both migrations (`V12`, `V13`) truncate their tables first so `user_id` can be `NOT NULL` from the start — do not make it nullable.
- Notifications are delivered via `messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", dto)`; the frontend subscribes to the fixed string `/user/queue/notifications` (no UUID in the topic name).
- `/ws/chat` must reject a STOMP `CONNECT` with a missing or invalid bearer token — the endpoint is no longer anonymous.
- No role-based (`@PreAuthorize`) authorization is added in this phase — authentication only, matching existing `SecurityConfig`.

---

## Task 1: Favorites identity migration

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V12__rename_client_id_to_user_id.sql`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteController.java`
- Modify: `hermes-backend/src/main/resources/openapi/favorites.yaml`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`

**Interfaces:**
- Produces: `FavoriteEntity.userId`, `FavoriteRepository.findByUserId/findByUserIdAndListingId/deleteByUserIdAndListingId/existsByUserIdAndListingId`, `FavoriteService.findByUserId/addFavorite/removeFavorite/isFavorite(UUID userId, ...)` — same method names as before minus `ByClientId` suffix changing to `ByUserId`.

- [ ] **Step 1: Write the migration (covers favorites, notifications, and agent_tasks together)**

Create `hermes-backend/src/main/resources/db/migration/V12__rename_client_id_to_user_id.sql`:

```sql
TRUNCATE TABLE notifications, agent_tasks, favorites CASCADE;

ALTER TABLE favorites RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_favorites_client_id RENAME TO idx_favorites_user_id;
ALTER TABLE favorites RENAME CONSTRAINT uq_favorites_client_listing TO uq_favorites_user_listing;

ALTER TABLE notifications RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_notifications_client_id_created RENAME TO idx_notifications_user_id_created;

ALTER TABLE agent_tasks RENAME COLUMN client_id TO user_id;
```

- [ ] **Step 2: Rename `FavoriteEntity.clientId` to `userId`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteEntity.java`, replace:

```java
    @Column(nullable = false)
    private UUID clientId;
```

with:

```java
    @Column(nullable = false)
    private UUID userId;
```

- [ ] **Step 3: Rename `FavoriteRepository` methods**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteRepository.java`:

```java
package com.kropholler.dev.hermes.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, UUID> {
    List<FavoriteEntity> findByUserId(UUID userId);
    Optional<FavoriteEntity> findByUserIdAndListingId(UUID userId, UUID listingId);
    void deleteByUserIdAndListingId(UUID userId, UUID listingId);
    boolean existsByUserIdAndListingId(UUID userId, UUID listingId);
}
```

- [ ] **Step 4: Rename `FavoriteService` methods**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteService.java`:

```java
package com.kropholler.dev.hermes.favorites;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository repository;

    @Transactional(readOnly = true)
    public List<FavoriteDto> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream()
                .map(f -> new FavoriteDto(f.getListingId(), f.getSavedAt()))
                .toList();
    }

    @Transactional
    public void addFavorite(UUID userId, UUID listingId) {
        if (!repository.existsByUserIdAndListingId(userId, listingId)) {
            FavoriteEntity f = new FavoriteEntity();
            f.setUserId(userId);
            f.setListingId(listingId);
            repository.save(f);
        }
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID listingId) {
        repository.deleteByUserIdAndListingId(userId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID listingId) {
        return repository.existsByUserIdAndListingId(userId, listingId);
    }
}
```

- [ ] **Step 5: Drop `clientId` from the OpenAPI spec**

In `hermes-backend/src/main/resources/openapi/favorites.yaml`, remove `clientId` from every path — the endpoints become identity-free:

```yaml
openapi: 3.0.3
info:
  title: Hermes Favorites API
  version: 1.0.0

paths:
  /api/favorites:
    get:
      operationId: getFavorites
      tags: [Favorites]
      responses:
        '200':
          description: List of favorites
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/FavoriteResponse'

  /api/favorites/{listingId}:
    put:
      operationId: addFavorite
      tags: [Favorites]
      parameters:
        - name: listingId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Added
    delete:
      operationId: removeFavorite
      tags: [Favorites]
      parameters:
        - name: listingId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Removed

components:
  schemas:
    FavoriteResponse:
      type: object
      properties:
        listingId:
          type: string
          format: uuid
        savedAt:
          type: string
          format: date-time

    ProblemDetail:
      type: object
      properties:
        type:
          type: string
          format: uri
        title:
          type: string
        status:
          type: integer
        detail:
          type: string
        instance:
          type: string
          format: uri
```

- [ ] **Step 6: Rewrite `FavoriteController` to derive identity from the JWT**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/FavoriteController.java`:

```java
package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import com.kropholler.dev.hermes.favorites.openapi.FavoritesApi;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FavoriteController implements FavoritesApi {

    private final FavoriteService favoriteService;
    private final FavoriteApiMapper favoriteApiMapper;

    @Override
    public ResponseEntity<List<FavoriteResponse>> getFavorites() {
        UUID userId = CurrentUser.current().id();
        List<FavoriteResponse> responses = favoriteService.findByUserId(userId)
            .stream().map(favoriteApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> addFavorite(UUID listingId) {
        favoriteService.addFavorite(CurrentUser.current().id(), listingId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavorite(UUID listingId) {
        favoriteService.removeFavorite(CurrentUser.current().id(), listingId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: Update `FavoriteServiceTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteServiceTest.java`:

```java
package com.kropholler.dev.hermes.favorites;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    FavoriteRepository repository;
    @InjectMocks
    FavoriteService service;

    @Test
    void findByUserId_returnsMappedDtos() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        FavoriteEntity favourite = new FavoriteEntity();
        favourite.setUserId(userId);
        favourite.setListingId(listingId);
        favourite.setSavedAt(now);

        when(repository.findByUserId(userId)).thenReturn(List.of(favourite));

        List<FavoriteDto> result = service.findByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().listingId()).isEqualTo(listingId);
        assertThat(result.getFirst().savedAt()).isEqualTo(now);
    }

    @Test
    void addFavorite_savesWhenNotAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(false);

        service.addFavorite(userId, listingId);

        ArgumentCaptor<FavoriteEntity> cap = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        assertThat(cap.getValue().getListingId()).isEqualTo(listingId);
    }

    @Test
    void addFavorite_doesNotSaveWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(true);

        service.addFavorite(userId, listingId);

        verify(repository, never()).save(any());
    }

    @Test
    void removeFavorite_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        service.removeFavorite(userId, listingId);

        verify(repository).deleteByUserIdAndListingId(userId, listingId);
    }

    @Test
    void isFavorite_returnsTrueWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(true);

        assertThat(service.isFavorite(userId, listingId)).isTrue();
    }

    @Test
    void isFavorite_returnsFalseWhenAbsent() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(false);

        assertThat(service.isFavorite(userId, listingId)).isFalse();
    }
}
```

- [ ] **Step 8: Update `FavoriteControllerTest` to authenticate with a real per-request JWT subject**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`:

```java
package com.kropholler.dev.hermes.favorites;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.favorites.openapi.FavoriteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FavoriteController.class)
@Import(SecurityConfig.class)
class FavoriteControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean
    FavoriteService favoriteService;
    @MockitoBean
    FavoriteApiMapper favoriteApiMapper;

    @Test
    void getFavorites_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-01-15T10:00:00Z");

        FavoriteDto dto = new FavoriteDto(listingId, savedAt);
        FavoriteResponse response = new FavoriteResponse();
        response.setListingId(listingId);
        response.setSavedAt(OffsetDateTime.ofInstant(savedAt, ZoneOffset.UTC));

        when(favoriteService.findByUserId(userId)).thenReturn(List.of(dto));
        when(favoriteApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/favorites")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].listingId").value(listingId.toString()))
            .andExpect(jsonPath("$[0].savedAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void addFavorite_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(put("/api/favorites/{listingId}", listingId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(favoriteService).addFavorite(eq(userId), eq(listingId));
    }

    @Test
    void removeFavorite_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/favorites/{listingId}", listingId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(favoriteService).removeFavorite(eq(userId), eq(listingId));
    }
}
```

- [ ] **Step 9: Run the tests**

Run: `mvn test -Dtest=FavoriteServiceTest,FavoriteControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 9 tests total (6 + 3), 0 failures.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V12__rename_client_id_to_user_id.sql \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/favorites/ \
        hermes-backend/src/main/resources/openapi/favorites.yaml \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/
git commit -m "feat(backend): derive favorites identity from the JWT instead of a client-supplied clientId"
```

---

## Task 2: Notifications identity migration + /user/queue delivery

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationDto.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationController.java`
- Modify: `hermes-backend/src/main/resources/openapi/notifications.yaml`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`

**Interfaces:**
- Consumes: `V12__rename_client_id_to_user_id.sql` (Task 1, already renames the `notifications` table).
- Produces: `NotificationEntity.userId`, `NotificationDto.userId`, `NotificationService.save(UUID taskId, UUID userId, NotificationContent content)`, `NotificationService.findByUserId(UUID userId)`, `NotificationService.countUnread(UUID userId)` — all delivering to `/user/queue/notifications` instead of `/topic/notifications/{id}`.

- [ ] **Step 1: Rename `NotificationEntity.clientId` to `userId`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java`, replace:

```java
    @Column(nullable = false)
    private UUID clientId;
```

with:

```java
    @Column(nullable = false)
    private UUID userId;
```

- [ ] **Step 2: Rename `NotificationRepository` methods**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationRepository.java`:

```java
package com.kropholler.dev.hermes.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadFalse(UUID userId);
}
```

- [ ] **Step 3: Rename `NotificationDto.clientId` to `userId`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationDto.java`:

```java
package com.kropholler.dev.hermes.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    UUID taskId,
    UUID userId,
    String title,
    String body,
    List<UUID> listingIds,
    boolean read,
    Instant createdAt,
    Instant emailSentAt
) {}
```

- [ ] **Step 4: Rewrite `NotificationService`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationService.java`:

```java
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
    public NotificationDto save(UUID taskId, UUID userId, NotificationContent content) {
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
        return new NotificationDto(n.getId(), n.getTaskId(), n.getUserId(),
            n.getTitle(), n.getBody(), listingIds, n.isRead(),
            n.getCreatedAt(), n.getEmailSentAt());
    }
}
```

- [ ] **Step 5: Drop `clientId` and rename the response field in the OpenAPI spec**

In `hermes-backend/src/main/resources/openapi/notifications.yaml`, remove the `clientId` query parameter from both `getNotifications` and `getUnreadCount`, and rename `NotificationResponse.clientId` to `userId`:

```yaml
openapi: 3.0.3
info:
  title: Hermes Notifications API
  version: 1.0.0

paths:
  /api/notifications:
    get:
      tags: [Notifications]
      operationId: getNotifications
      responses:
        '200':
          description: Recent notifications
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/NotificationResponse'

  /api/notifications/unread-count:
    get:
      tags: [Notifications]
      operationId: getUnreadCount
      responses:
        '200':
          description: Unread notification count
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UnreadCountResponse'

  /api/notifications/{id}/read:
    patch:
      tags: [Notifications]
      operationId: markNotificationRead
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Marked read

components:
  schemas:
    NotificationResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        taskId:
          type: string
          format: uuid
        userId:
          type: string
          format: uuid
        title:
          type: string
        body:
          type: string
        listingIds:
          type: array
          items:
            type: string
            format: uuid
        read:
          type: boolean
        createdAt:
          type: string
          format: date-time
        emailSentAt:
          type: string
          format: date-time

    UnreadCountResponse:
      type: object
      properties:
        count:
          type: integer
          format: int64

    ProblemDetail:
      type: object
      properties:
        type:
          type: string
          format: uri
        title:
          type: string
        status:
          type: integer
        detail:
          type: string
        instance:
          type: string
          format: uri
```

- [ ] **Step 6: Rewrite `NotificationController`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationController.java`:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import com.kropholler.dev.hermes.notification.openapi.NotificationsApi;
import com.kropholler.dev.hermes.notification.openapi.UnreadCountResponse;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationsApi {

    private final NotificationService notificationService;
    private final NotificationApiMapper notificationApiMapper;

    @Override
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        List<NotificationResponse> responses = notificationService.findByUserId(CurrentUser.current().id())
            .stream().map(notificationApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        UnreadCountResponse r = new UnreadCountResponse();
        r.setCount(notificationService.countUnread(CurrentUser.current().id()));
        return ResponseEntity.ok(r);
    }

    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: Update `NotificationServiceTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationServiceTest.java`:

```java
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
        doThrow(new JsonProcessingException("forced") {}).when(objectMapper).writeValueAsString(any());
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
    void markRead_whenFound_setsReadAndSaves() {
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
        UUID notificationId = UUID.randomUUID();
        when(repo.findById(notificationId)).thenReturn(Optional.empty());

        service.markRead(notificationId);

        verify(repo, never()).save(any());
    }
}
```

- [ ] **Step 8: Update `NotificationControllerTest` to authenticate with a real per-request JWT subject**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean
    NotificationService notificationService;
    @MockitoBean
    NotificationApiMapper notificationApiMapper;

    @Test
    void getNotifications_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(notifId, taskId, userId,
            "New listing found", "Check it out", List.of(listingId),
            false, Instant.parse("2026-06-19T08:00:00Z"), null);

        NotificationResponse response = new NotificationResponse();
        response.setId(notifId);
        response.setTaskId(taskId);
        response.setUserId(userId);
        response.setTitle("New listing found");
        response.setBody("Check it out");
        response.setListingIds(List.of(listingId));
        response.setRead(false);

        when(notificationService.findByUserId(userId)).thenReturn(List.of(dto));
        when(notificationApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/notifications")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(notifId.toString()))
            .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
            .andExpect(jsonPath("$[0].userId").value(userId.toString()))
            .andExpect(jsonPath("$[0].title").value("New listing found"))
            .andExpect(jsonPath("$[0].body").value("Check it out"))
            .andExpect(jsonPath("$[0].listingIds[0]").value(listingId.toString()))
            .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void getUnreadCount_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.countUnread(userId)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(5));

        verify(notificationService).countUnread(eq(userId));
    }

    @Test
    void markNotificationRead_callsServiceAndReturns204() throws Exception {
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                .with(jwt()))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(notifId);
    }
}
```

- [ ] **Step 9: Run the tests**

Run: `mvn test -Dtest=NotificationServiceTest,NotificationControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 12 tests total (9 + 3), 0 failures.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/ \
        hermes-backend/src/main/resources/openapi/notifications.yaml \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/
git commit -m "feat(backend): derive notification identity from the JWT, deliver via /user/queue"
```

---

## Task 3: Agent Tasks identity migration (service, controller, tools, handlers, executor)

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskDto.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutor.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/DigestTaskHandler.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/ResearchTaskHandler.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TaskTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/tool/GetFavouriteListingsTool.java`
- Modify: `hermes-backend/src/main/resources/openapi/agent-tasks.yaml`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutorTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/DigestTaskHandlerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/ResearchTaskHandlerTest.java`

**Interfaces:**
- Consumes: `V12__rename_client_id_to_user_id.sql` (Task 1, already renames the `agent_tasks` table); `NotificationService.save(UUID taskId, UUID userId, ...)` (Task 2).
- Produces: `AgentTaskEntity.userId`, `AgentTaskService.createWatch/createResearch/createDigest/findByUserId(UUID userId, ...)`, `GetFavouriteListingsTool(UUID userId, ...)` (constructor param renamed), `TaskTool.userId` (protected field renamed) — consumed by `SaveWatchTool`/`ListWatchesTool`/`TriggerDigestTool`/`TriggerResearchTool`/`AgentChatToolProvider`, which are in turn consumed by Task 5's chat wiring.

This task is a single mechanical rename (`clientId` → `userId`) applied consistently across the whole agent-tasks module and everywhere it touches favorites-tool construction. Apply the exact renames below; nothing here changes any behavior, only names.

- [ ] **Step 1: `AgentTaskEntity`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java`, replace:

```java
    @Column(nullable = false)
    private UUID clientId;
```

with:

```java
    @Column(nullable = false)
    private UUID userId;
```

- [ ] **Step 2: `AgentTaskRepository`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepository.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {
    List<AgentTaskEntity> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTaskEntity> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AgentTaskStatus status);
}
```

- [ ] **Step 3: `AgentTaskDto`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskDto.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import java.time.Instant;
import java.util.UUID;

public record AgentTaskDto(
    UUID id,
    AgentTaskType type,
    AgentTaskStatus status,
    UUID userId,
    String name,
    String schedule,
    Instant lastRunAt,
    Instant nextRunAt,
    Instant createdAt
) {}
```

- [ ] **Step 4: `AgentTaskService`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.DigestPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.ResearchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskRepository agentTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentTaskDto createWatch(UUID userId, String name, WatchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(payload));
        task.setSchedule("0 0 8 * * *");
        task.setNextRunAt(computeNext("0 0 8 * * *"));
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskDto createResearch(UUID userId, String prompt) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(userId);
        task.setName("Research: " + prompt.substring(0, Math.min(60, prompt.length())));
        task.setPayload(serialize(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskDto createDigest(UUID userId, String name, List<String> cities) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.DIGEST);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(computeNext("0 0 8 * * MON"));
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<AgentTaskDto> findByUserId(UUID userId) {
        return agentTaskRepository
            .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AgentTaskStatus.ACTIVE)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(UUID taskId) {
        agentTaskRepository.deleteById(taskId);
    }

    @Transactional
    public void markRan(AgentTaskEntity task) {
        task.setLastRunAt(Instant.now());
        if (task.getSchedule() != null) {
            task.setNextRunAt(computeNext(task.getSchedule()));
        } else {
            task.setStatus(AgentTaskStatus.COMPLETED);
        }
        agentTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> findDueTasks() {
        return agentTaskRepository.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());
    }

    private Instant computeNext(String schedule) {
        CronExpression expr = CronExpression.parse(schedule);
        ZonedDateTime next = expr.next(ZonedDateTime.now());
        return next != null ? next.toInstant() : Instant.now().plus(1, ChronoUnit.DAYS);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent task payload", e);
        }
    }

    public AgentTaskDto toDto(AgentTaskEntity t) {
        return new AgentTaskDto(t.getId(), t.getType(), t.getStatus(), t.getUserId(),
            t.getName(), t.getSchedule(), t.getLastRunAt(), t.getNextRunAt(), t.getCreatedAt());
    }
}
```

- [ ] **Step 5: `AgentTaskController`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTasksApi;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgentTaskController implements AgentTasksApi {

    private final AgentTaskService agentTaskService;
    private final AgentTaskApiMapper agentTaskApiMapper;

    @Override
    public ResponseEntity<List<AgentTaskResponse>> getAgentTasks() {
        List<AgentTaskResponse> responses = agentTaskService.findByUserId(CurrentUser.current().id())
            .stream().map(agentTaskApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: `AgentTaskExecutor`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutor.java`, replace:

```java
            handler.handle(task).ifPresent(content ->
                notificationService.save(task.getId(), task.getClientId(), content));
```

with:

```java
            handler.handle(task).ifPresent(content ->
                notificationService.save(task.getId(), task.getUserId(), content));
```

- [ ] **Step 7: `DigestTaskHandler`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/DigestTaskHandler.java`, replace:

```java
        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID clientId = task.getClientId();
```

with:

```java
        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID userId = task.getUserId();
```

and replace:

```java
        var favTool       = new GetFavouriteListingsTool(clientId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);
```

with:

```java
        var favTool       = new GetFavouriteListingsTool(userId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);
```

- [ ] **Step 8: `ResearchTaskHandler`**

Apply the identical two replacements as Step 7 to `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/ResearchTaskHandler.java` (same line shapes: `UUID clientId = task.getClientId();` → `UUID userId = task.getUserId();`, and the `GetFavouriteListingsTool(clientId, ...)` construction → `GetFavouriteListingsTool(userId, ...)`).

- [ ] **Step 9: `TaskTool` base class**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TaskTool.java`:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
    }
}
```

- [ ] **Step 10: `SaveWatchTool`, `ListWatchesTool`, `TriggerDigestTool`, `TriggerResearchTool`**

In each of these four files, apply the identical mechanical rename: the constructor parameter `UUID clientId` becomes `UUID userId`, the `super(clientId, agentTaskService)` call becomes `super(userId, agentTaskService)`, and every use of the (now-removed) `clientId` field inside a method body becomes `userId`:

- `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`: `protected SaveWatchTool(UUID clientId, ...)` → `protected SaveWatchTool(UUID userId, ...)`; `super(clientId, agentTaskService)` → `super(userId, agentTaskService)`; `agentTaskService.createWatch(clientId, watchName, payload)` → `agentTaskService.createWatch(userId, watchName, payload)`.
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`: `protected ListWatchesTool(UUID clientId, ...)` → `protected ListWatchesTool(UUID userId, ...)`; `super(clientId, agentTaskService)` → `super(userId, agentTaskService)`; `agentTaskService.findByClientId(clientId)` → `agentTaskService.findByUserId(userId)`.
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`: `protected TriggerDigestTool(UUID clientId, ...)` → `protected TriggerDigestTool(UUID userId, ...)`; `super(clientId, agentTaskService)` → `super(userId, agentTaskService)`; `agentTaskService.createDigest(clientId, name, cityList)` → `agentTaskService.createDigest(userId, name, cityList)`.
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`: `protected TriggerResearchTool(UUID clientId, ...)` → `protected TriggerResearchTool(UUID userId, ...)`; `super(clientId, agentTaskService)` → `super(userId, agentTaskService)`; `agentTaskService.createResearch(clientId, prompt)` → `agentTaskService.createResearch(userId, prompt)`.

- [ ] **Step 11: `AgentChatToolProvider`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java`:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.ChatToolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentChatToolProvider implements ChatToolProvider {

    private final AgentTaskService agentTaskService;

    @Override
    public List<Object> provideTools(UUID userId) {
        return List.of(
            new SaveWatchTool(userId, agentTaskService),
            new TriggerResearchTool(userId, agentTaskService),
            new TriggerDigestTool(userId, agentTaskService),
            new ListWatchesTool(userId, agentTaskService)
        );
    }
}
```

(This changes the `ChatToolProvider` interface's parameter name too — Task 5 updates the interface declaration itself; this file must compile against the renamed interface, so land Task 5 in the same session before running the full build, or expect a transient compile error until Task 5 lands. Since tasks in this plan are applied sequentially in one working tree, this is fine.)

- [ ] **Step 12: `GetFavouriteListingsTool`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/tool/GetFavouriteListingsTool.java`:

```java
package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.favorites.FavoriteDto;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GetFavouriteListingsTool {

    private final UUID userId;
    private final FavoriteService favoriteService;
    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;
    private final Counter callCounter;

    public GetFavouriteListingsTool(UUID userId,
                                     FavoriteService favoriteService,
                                     ListingService listingService,
                                     ChatListingCardMapper mapper,
                                     AtomicReference<List<ChatListingCard>> resultHolder,
                                     MeterRegistry meterRegistry) {
        this.userId = userId;
        this.favoriteService = favoriteService;
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "getFavouriteListings");
    }

    @Tool(description = "Get the user's saved (favourited) listings. "
            + "Call this when the user asks to see their saved properties, favourites, or wishlist.")
    public String getFavouriteListings() {
        log.info("getFavouriteListings called: userId={}", userId);
        callCounter.increment();

        List<FavoriteDto> favourites = favoriteService.findByUserId(userId);
        if (favourites.isEmpty()) {
            return "You have no saved listings yet. You can save a listing by clicking the heart icon on its detail page.";
        }

        List<ChatListingCard> cards = favourites.stream()
                .map(f -> listingService.findById(f.listingId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> mapper.toChatListingCard(opt.get()))
                .toList();
        resultHolder.set(cards);

        if (cards.isEmpty()) {
            return "Your saved listings could not be found — they may have been removed.";
        }

        StringBuilder sb = new StringBuilder("You have ").append(cards.size()).append(" saved listing(s):\n\n");
        for (ChatListingCard c : cards) {
            sb.append("- ").append(c.street()).append(" ").append(c.houseNumber());
            if (c.houseNumberAddition() != null) sb.append(c.houseNumberAddition());
            sb.append(", ").append(c.city());
            if (c.currentPrice() != null) sb.append(" — €").append(String.format("%,d", c.currentPrice()).replace(",", "."));
            sb.append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 13: Drop `clientId` and rename the response field in the OpenAPI spec**

Replace the full contents of `hermes-backend/src/main/resources/openapi/agent-tasks.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Hermes Agent Tasks API
  version: 1.0.0

paths:
  /api/agent-tasks:
    get:
      tags: [AgentTasks]
      operationId: getAgentTasks
      responses:
        '200':
          description: List of active agent tasks
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/AgentTaskResponse'

  /api/agent-tasks/{id}:
    delete:
      tags: [AgentTasks]
      operationId: deleteAgentTask
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Deleted

components:
  schemas:
    AgentTaskResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        type:
          type: string
        status:
          type: string
        userId:
          type: string
          format: uuid
        name:
          type: string
        schedule:
          type: string
        lastRunAt:
          type: string
          format: date-time
        nextRunAt:
          type: string
          format: date-time
        createdAt:
          type: string
          format: date-time

    ProblemDetail:
      type: object
      properties:
        type:
          type: string
          format: uri
        title:
          type: string
        status:
          type: integer
        detail:
          type: string
        instance:
          type: string
          format: uri
```

- [ ] **Step 14: Update the existing test files**

Apply the same mechanical rename to each of these existing test files: every `UUID clientId = UUID.randomUUID();` (or similarly named local variable) becomes `UUID userId = ...`; every `task.setClientId(...)` becomes `task.setUserId(...)`; every `findByClientId`/`findAllByClientIdAndStatusOrderByCreatedAtDesc` becomes `findByUserId`/`findAllByUserIdAndStatusOrderByCreatedAtDesc`; every `service.createWatch(clientId, ...)`/`createResearch(clientId, ...)`/`createDigest(clientId, ...)` becomes `(userId, ...)`:

- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutorTest.java` (also rename any `task.getClientId()`/`task.setClientId(...)` used when constructing/verifying against the executor's `notificationService.save(...)` call, matching Step 6's `getUserId()` rename)
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/DigestTaskHandlerTest.java` (rename any `task.setClientId(...)` used to build the `AgentTaskEntity` fixture, and any assertion referencing the tool construction's identity argument)
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/ResearchTaskHandlerTest.java` (same as above)

Additionally, update `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`: this is a `@WebMvcTest` — replace its full contents so it authenticates like `FavoriteControllerTest`/`NotificationControllerTest` now do:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTaskController.class)
@Import(SecurityConfig.class)
class AgentTaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean AgentTaskService agentTaskService;
    @MockitoBean AgentTaskApiMapper agentTaskApiMapper;

    @Test
    void getAgentTasks_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(taskId, AgentTaskType.WATCH, AgentTaskStatus.ACTIVE,
            userId, "My watch", "0 0 8 * * *", Instant.parse("2026-06-01T08:00:00Z"),
            Instant.parse("2026-06-20T08:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));

        AgentTaskResponse response = new AgentTaskResponse();
        response.setId(taskId);
        response.setType("WATCH");
        response.setStatus("ACTIVE");
        response.setName("My watch");
        response.setSchedule("0 0 8 * * *");

        when(agentTaskService.findByUserId(userId)).thenReturn(List.of(dto));
        when(agentTaskApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/agent-tasks")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(taskId.toString()))
            .andExpect(jsonPath("$[0].type").value("WATCH"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].name").value("My watch"))
            .andExpect(jsonPath("$[0].schedule").value("0 0 8 * * *"));

        verify(agentTaskService).findByUserId(eq(userId));
    }

    @Test
    void getAgentTasks_emptyList_returnsEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();
        when(agentTaskService.findByUserId(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/agent-tasks")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteAgentTask_callsServiceAndReturns204() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt()))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(taskId);
    }
}
```

- [ ] **Step 15: Run the tests**

Run: `mvn test -Dtest=AgentTaskServiceTest,AgentTaskControllerTest,AgentTaskExecutorTest,DigestTaskHandlerTest,ResearchTaskHandlerTest,AgentChatToolProviderTest -f hermes-backend/pom.xml`
Expected: PASS, 0 failures across all six test classes.

- [ ] **Step 16: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/ \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/tool/GetFavouriteListingsTool.java \
        hermes-backend/src/main/resources/openapi/agent-tasks.yaml \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/
git commit -m "feat(backend): derive agent-task identity from the JWT instead of a client-supplied clientId"
```

---

## Task 4: WebSocket authentication

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptor.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WebSocketConfig.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptorTest.java`

**Interfaces:**
- Consumes: `SecurityConfig.realmRoleAuthorities(Jwt)` (phase 1, same package `com.kropholler.dev.hermes.config`, package-private-visible static method); the auto-configured `JwtDecoder` bean.
- Produces: every STOMP `CONNECT` on `/ws/chat` now requires a valid `Authorization: Bearer <token>` STOMP header; the resulting `Principal` (a `JwtAuthenticationToken`) is available to `@MessageMapping` methods via `@Header("simpUser") Principal principal` (used by Task 5).

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptorTest.java`:

```java
package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsAuthChannelInterceptorTest {

    @Mock JwtDecoder jwtDecoder;
    @Mock MessageChannel channel;

    WsAuthChannelInterceptor interceptor;

    private Jwt validJwt(UUID subject) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }

    private Message<byte[]> connectFrameWithAuthHeader(String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (headerValue != null) {
            accessor.addNativeHeader("Authorization", headerValue);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        interceptor = new WsAuthChannelInterceptor(jwtDecoder);
    }

    @Test
    void preSend_withValidBearerToken_setsAuthenticatedPrincipal() {
        UUID subject = UUID.randomUUID();
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt(subject));

        Message<?> result = interceptor.preSend(connectFrameWithAuthHeader("Bearer valid-token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        JwtAuthenticationToken principal = (JwtAuthenticationToken) accessor.getUser();
        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo(subject.toString());
        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority())
            .contains("ROLE_USER");
    }

    @Test
    void preSend_withMissingAuthHeader_throws() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrameWithAuthHeader(null), channel))
            .isInstanceOf(org.springframework.messaging.MessagingException.class);
    }

    @Test
    void preSend_withInvalidToken_throws() {
        when(jwtDecoder.decode("garbage")).thenThrow(
            new org.springframework.security.oauth2.jwt.JwtException("invalid"));

        assertThatThrownBy(() -> interceptor.preSend(connectFrameWithAuthHeader("Bearer garbage"), channel))
            .isInstanceOf(org.springframework.messaging.MessagingException.class);
    }

    @Test
    void preSend_nonConnectFrame_passesThroughUnchanged() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WsAuthChannelInterceptorTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `WsAuthChannelInterceptor` does not exist.

- [ ] **Step 3: Write `WsAuthChannelInterceptor`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptor.java`:

```java
package com.kropholler.dev.hermes.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Missing or malformed Authorization header on STOMP CONNECT");
        }

        String token = authHeader.substring("Bearer ".length());
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new MessagingException("Invalid bearer token on STOMP CONNECT", e);
        }

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, SecurityConfig.realmRoleAuthorities(jwt));
        accessor.setUser(authentication);
        return message;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WsAuthChannelInterceptorTest -f hermes-backend/pom.xml`
Expected: PASS, 4 tests, 0 failures.

- [ ] **Step 5: Register the interceptor in `WebSocketConfig`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WebSocketConfig.java`, add the import `import org.springframework.messaging.simp.config.ChannelRegistration;`, add a constructor taking `WsAuthChannelInterceptor` (the class becomes non-static-only, needs field + constructor), and add the `configureClientInboundChannel` override. Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WebSocketConfig.java`:

```java
package com.kropholler.dev.hermes.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsAuthChannelInterceptor wsAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(wsAuthChannelInterceptor);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        return false;
    }
}
```

Note: `config.enableSimpleBroker("/topic", "/queue")` adds `/queue` to the simple broker's recognized prefixes — required for `convertAndSendToUser`'s `/user/queue/notifications` destination to resolve correctly (Spring's `UserDestinationMessageHandler` rewrites `/user/queue/notifications` into a session-specific `/queue/notifications-user<N>` internally, which must match a broker-registered prefix). The `/**` origin-permissive comment from phase 1 (`"Open to all origins: this is an unauthenticated endpoint..."`) is removed since the endpoint is no longer unauthenticated — origins are still permissive (`"*"`) for local/dev convenience, unchanged from before.

- [ ] **Step 6: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS. (Existing `WebSocketConfig`-adjacent tests, if any, should be unaffected since no endpoint mapping changed, only broker/interceptor config.)

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptor.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/config/WebSocketConfig.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/config/WsAuthChannelInterceptorTest.java
git commit -m "feat(backend): require a valid JWT on STOMP CONNECT for /ws/chat"
```

---

## Task 5: Chat identity migration

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V13__add_user_id_to_chat_messages.sql`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRequest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/AiChatService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatController.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/AiChatServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatControllerTest.java`

**Interfaces:**
- Consumes: `WsAuthChannelInterceptor` (Task 4, sets the STOMP `Principal`); `ChatToolProvider.provideTools(UUID userId)` (renamed parameter, implemented by `AgentChatToolProvider` from Task 3); `GetFavouriteListingsTool(UUID userId, ...)` (Task 3).
- Produces: `AiChatService.startStream(UUID sessionId, UUID userId, String userMessage)` (no more null-fallback), `AiChatService.saveUserMessage(UUID sessionId, UUID userId, String content)`, `AiChatService.saveAssistantMessage(UUID sessionId, UUID userId, String content)`.

- [ ] **Step 1: Write the migration**

Create `hermes-backend/src/main/resources/db/migration/V13__add_user_id_to_chat_messages.sql`:

```sql
TRUNCATE TABLE chat_messages;

ALTER TABLE chat_messages ADD COLUMN user_id UUID NOT NULL;
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
```

- [ ] **Step 2: Add `userId` to `ChatMessageEntity`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`, add a new field after `sessionId`:

```java
    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID userId;
```

- [ ] **Step 3: Drop `clientId` from `ChatMessageRequest`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRequest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChatMessageRequest(
        @NotNull UUID sessionId,
        @NotBlank String message
) {}
```

- [ ] **Step 4: Update `ChatToolProvider` interface parameter name**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java`, replace the method declaration and its Javadoc:

```java
    /**
     * Return tool instances scoped to the given user.
     * Called once per chat request — implementations may create new instances each time.
     *
     * @param userId the authenticated user's UUID for this chat session
     * @return a list of Spring AI tool objects (annotated with {@code @Tool})
     */
    List<Object> provideTools(UUID userId);
```

- [ ] **Step 5: Rewrite `AiChatService`'s identity handling**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/AiChatService.java`:

Replace:

```java
    @Transactional
    public void saveUserMessage(UUID sessionId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole("USER");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    @Transactional
    public void saveAssistantMessage(UUID sessionId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
```

with:

```java
    @Transactional
    public void saveUserMessage(UUID sessionId, UUID userId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("USER");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    @Transactional
    public void saveAssistantMessage(UUID sessionId, UUID userId, String content) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }
```

Replace:

```java
    public StreamHandle startStream(UUID sessionId, UUID clientId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
```

with:

```java
    public StreamHandle startStream(UUID sessionId, UUID userId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
```

Replace:

```java
        // clientId falls back to sessionId so favourites work even if the frontend
        // hasn't been updated to send a separate clientId yet.
        UUID effectiveClientId = clientId != null ? clientId : sessionId;

        ListingSearchTool searchTool = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetListingSummaryTool summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        GetPriceHistoryTool historyTool = new GetPriceHistoryTool(listingService, meterRegistry);
        CompareListingsTool compareTool = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        FindPriceDropTool priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetFavouriteListingsTool favouritesTool = new GetFavouriteListingsTool(
                effectiveClientId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        List<Object> allTools = new ArrayList<>(List.of(
                searchTool, summaryTool, historyTool, compareTool, priceDropTool, favouritesTool));
        for (ChatToolProvider provider : chatToolProviders) {
            allTools.addAll(provider.provideTools(effectiveClientId));
        }
```

with:

```java
        ListingSearchTool searchTool = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetListingSummaryTool summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        GetPriceHistoryTool historyTool = new GetPriceHistoryTool(listingService, meterRegistry);
        CompareListingsTool compareTool = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        FindPriceDropTool priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        GetFavouriteListingsTool favouritesTool = new GetFavouriteListingsTool(
                userId, favoriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        List<Object> allTools = new ArrayList<>(List.of(
                searchTool, summaryTool, historyTool, compareTool, priceDropTool, favouritesTool));
        for (ChatToolProvider provider : chatToolProviders) {
            allTools.addAll(provider.provideTools(userId));
        }
```

- [ ] **Step 6: Rewrite `ChatController` to derive identity from the STOMP principal**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatController.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;
    private final SimpMessagingTemplate messaging;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/chat")
    public void handleMessage(ChatMessageRequest request, @Header("simpUser") Principal principal) {
        if (request == null || request.sessionId() == null || request.message() == null || request.message().isBlank()) {
            log.warn("Received invalid chat request: {}", request);
            return;
        }

        UUID userId = CurrentUser.from((Jwt) ((JwtAuthenticationToken) principal).getPrincipal()).id();

        String destination = "/topic/chat/" + request.sessionId();
        Timer.Sample requestTimer = Timer.start(meterRegistry);
        Timer.Sample ttftTimer = Timer.start(meterRegistry);
        long startNanos = System.nanoTime();
        String outcome = "success";

        log.info("AI chat request: session={}, messageLength={}", request.sessionId(), request.message().length());

        try {
            AiChatService.StreamHandle handle = aiChatService.startStream(
                    request.sessionId(), userId, request.message());

            String response = streamTokens(handle, destination, request.sessionId(), ttftTimer, startNanos);

            aiChatService.saveUserMessage(request.sessionId(), userId, request.message());
            if (!response.isBlank()) {
                aiChatService.saveAssistantMessage(request.sessionId(), userId, response);
            }
            messaging.convertAndSend(destination, new ResultFrame("RESULT", handle.resultHolder().get()));

            log.info("AI chat completed: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos));

        } catch (Exception e) {
            outcome = "error";
            log.error("AI chat error: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));

        } finally {
            requestTimer.stop(Timer.builder("hermes.ai.chat.duration")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            meterRegistry.counter("hermes.ai.chat.requests", "outcome", outcome).increment();
        }
    }

    private String streamTokens(AiChatService.StreamHandle handle, String destination,
                                UUID sessionId, Timer.Sample ttftTimer, long startNanos) {
        StringBuilder response = new StringBuilder();
        AtomicBoolean firstToken = new AtomicBoolean(true);

        handle.tokens()
                .doOnNext(token -> {
                    if (firstToken.compareAndSet(true, false)) {
                        ttftTimer.stop(Timer.builder("hermes.ai.chat.time_to_first_token")
                                .description("Time from request to first text token")
                                .register(meterRegistry));
                        log.info("AI chat first token: session={}, ttft={}ms", sessionId, elapsedMs(startNanos));
                    }
                    response.append(token);
                    messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
                })
                .blockLast();

        return response.toString().strip();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
```

- [ ] **Step 7: Update `AiChatServiceTest`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/AiChatServiceTest.java`:

Replace the `saveUserMessage`/`saveAssistantMessage` tests:

```java
    @Test
    void saveUserMessage_savesEntityWithUserRole() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveUserMessage(sessionId, userId, "Hello");

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(captor.getValue().getContent()).isEqualTo("Hello");
        assertThat(captor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void saveAssistantMessage_savesEntityWithAssistantRole() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveAssistantMessage(sessionId, userId, "I found 3 houses.");

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ASSISTANT");
        assertThat(captor.getValue().getContent()).isEqualTo("I found 3 houses.");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }
```

Replace the two `startStream_nullClientId_fallsBackToSessionId`/`startStream_withExplicitClientId_usesClientId` tests (the null-fallback no longer exists) with a single test:

```java
    @Test
    void startStream_usesGivenUserId() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        stubStream(Flux.just("Hi"));

        AiChatService.StreamHandle handle = service.startStream(sessionId, userId, "hello");

        assertThat(handle).isNotNull();
        assertThat(handle.resultHolder().get()).isEmpty();
    }
```

Replace every remaining `service.startStream(sessionId, null, ...)` call in the other tests (`startStream_historyWithUserAndAssistantRoles_mapsBoth`, `startStream_unknownRole_throwsIllegalStateException`) with `service.startStream(sessionId, UUID.randomUUID(), ...)` (a fresh random user id — these tests don't assert on identity, they just need any non-null value since `Objects.requireNonNull(userId, ...)` now rejects `null`).

Replace the `startStream_withChatToolProvider_addsProviderTools` test:

```java
    @Test
    void startStream_withChatToolProvider_addsProviderTools() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        service = new AiChatService(chatClient, chatMessageRepository, listingService,
                chatListingCardMapper, listingSummaryService, favoriteService,
                List.of(chatToolProvider), new SimpleMeterRegistry());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
        when(chatToolProvider.provideTools(userId)).thenReturn(List.of(new Object()));
        stubStream(Flux.just("done"));

        AiChatService.StreamHandle handle = service.startStream(sessionId, userId, "hi");

        verify(chatToolProvider).provideTools(userId);
        assertThat(handle).isNotNull();
    }
```

- [ ] **Step 8: Update `ChatControllerTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatControllerTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock AiChatService aiChatService;
    @Mock SimpMessagingTemplate messaging;
    ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(aiChatService, messaging, new SimpleMeterRegistry());
    }

    private Principal principalFor(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void handleMessage_streamsTokensAndSendsEmptyResult() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses in Utrecht");
        AiChatService.StreamHandle handle = handle(Flux.just("I ", "found ", "nothing."), List.of());
        when(aiChatService.startStream(sessionId, userId, request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(aiChatService).startStream(sessionId, userId, request.message());
        verify(messaging, times(3)).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("TOKEN")));
        verify(aiChatService).saveUserMessage(sessionId, userId, request.message());
        verify(aiChatService).saveAssistantMessage(sessionId, userId, "I found nothing.");
        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf && rf.listings().isEmpty()));
    }

    @Test
    void handleMessage_withListings_sendsResultFrameWithCards() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "3 bedrooms Amsterdam");
        ChatListingCard card = new ChatListingCard(UUID.randomUUID(), "Keizersgracht", "1",
                null, "Amsterdam", "Noord-Holland", 450000, 3, 85, "A", "FOR_SALE");
        AiChatService.StreamHandle handle = handle(Flux.just("Here you go."), List.of(card));
        when(aiChatService.startStream(sessionId, userId, request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame rf
                        && rf.listings().size() == 1
                        && rf.listings().get(0).city().equals("Amsterdam")));
    }

    @Test
    void handleMessage_whitespaceOnlyResponse_doesNotSaveAssistantMessage() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "hello");
        AiChatService.StreamHandle handle = handle(Flux.just("   "), List.of());
        when(aiChatService.startStream(sessionId, userId, request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(aiChatService).saveUserMessage(sessionId, userId, request.message());
        verify(aiChatService, never()).saveAssistantMessage(any(), any(), any());
    }

    @Test
    void handleMessage_serviceThrows_sendsErrorTokenFrame() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "Show me houses");
        AiChatService.StreamHandle handle = handle(Flux.error(new RuntimeException("LLM timeout")), List.of());
        when(aiChatService.startStream(sessionId, userId, request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(messaging).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof TokenFrame tf && tf.type().equals("ERROR")));
        verify(aiChatService, never()).saveUserMessage(any(), any(), any());
        verify(aiChatService, never()).saveAssistantMessage(any(), any(), any());
        verify(messaging, never()).convertAndSend(
                eq("/topic/chat/" + sessionId),
                (Object) argThat(obj -> obj instanceof ResultFrame));
    }

    @Test
    void handleMessage_nullRequest_returnsEarlyWithoutStreaming() {
        controller.handleMessage(null, principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any());
        verify(messaging, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void handleMessage_nullSessionId_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(null, "hello"), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    @Test
    void handleMessage_nullMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), null), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    @Test
    void handleMessage_blankMessage_returnsEarlyWithoutStreaming() {
        controller.handleMessage(new ChatMessageRequest(UUID.randomUUID(), "   "), principalFor(UUID.randomUUID()));

        verify(aiChatService, never()).startStream(any(), any(), any());
    }

    private AiChatService.StreamHandle handle(Flux<String> tokens, List<ChatListingCard> cards) {
        return new AiChatService.StreamHandle(tokens, new AtomicReference<>(cards));
    }
}
```

- [ ] **Step 9: Run the tests**

Run: `mvn test -Dtest=AiChatServiceTest,ChatControllerTest,AgentChatToolProviderTest -f hermes-backend/pom.xml`
Expected: PASS, 0 failures.

- [ ] **Step 10: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS, all tests pass. Also run `mvn test -Dtest=HermesBackendApplicationTests -f hermes-backend/pom.xml` to confirm Spring Modulith's module structure still verifies cleanly (no new cross-module boundary violations were introduced by this task's changes).

- [ ] **Step 11: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V13__add_user_id_to_chat_messages.sql \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/
git commit -m "feat(backend): derive chat identity from the authenticated STOMP principal"
```

---

## Task 6: Frontend — drop clientId, authenticate the WebSocket

**Files:**
- Modify: `hermes-frontend/src/app/core/favorites.service.ts`
- Modify: `hermes-frontend/src/app/core/notifications.service.ts`
- Modify: `hermes-frontend/src/app/core/agent-task.service.ts`
- Modify: `hermes-frontend/src/app/core/chat.service.ts`

**Interfaces:**
- Consumes: `inject(Keycloak)` (phase 1, from `keycloak-js`, already registered via `provideKeycloak` in `app.config.ts`); the existing bearer-token HTTP interceptor (phase 1, unaffected — still attaches `Authorization` to `/api/**` automatically).

- [ ] **Step 1: `favorites.service.ts` — drop clientId from every URL**

Replace the full contents of `hermes-frontend/src/app/core/favorites.service.ts`:

```ts
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FavoriteDto } from './api.types';

@Injectable({ providedIn: 'root' })
export class FavoritesService {
  private readonly http = inject(HttpClient);

  private readonly _favorites = signal<Set<string>>(new Set());
  readonly favoriteIds = this._favorites.asReadonly();

  constructor() {
    this.loadFavorites();
  }

  isFavorite(listingId: string): boolean {
    return this._favorites().has(listingId);
  }

  toggle(listingId: string): void {
    if (this.isFavorite(listingId)) {
      this.remove(listingId);
    } else {
      this.add(listingId);
    }
  }

  private add(listingId: string): void {
    this._favorites.update(set => new Set([...set, listingId]));
    this.http.put(`/api/favorites/${listingId}`, {}).subscribe({
      error: () => this._favorites.update(set => { const s = new Set(set); s.delete(listingId); return s; }),
    });
  }

  private remove(listingId: string): void {
    this._favorites.update(set => { const s = new Set(set); s.delete(listingId); return s; });
    this.http.delete(`/api/favorites/${listingId}`).subscribe({
      error: () => this._favorites.update(set => new Set([...set, listingId])),
    });
  }

  private loadFavorites(): void {
    this.http.get<FavoriteDto[]>(`/api/favorites`).subscribe({
      next: dtos => this._favorites.set(new Set(dtos.map(d => d.listingId))),
    });
  }
}
```

- [ ] **Step 2: `notifications.service.ts` — drop clientId, subscribe to /user/queue**

Replace the full contents of `hermes-frontend/src/app/core/notifications.service.ts`:

```ts
import { Injectable, signal, computed, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { catchError, of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { NotificationResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class NotificationsService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly keycloak = inject(Keycloak);

  private readonly stompClient: Client;
  private subscription?: StompSubscription;

  private readonly _notifications = signal<NotificationResponse[]>([]);
  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = computed(() => this._notifications().filter(n => !n.read).length);

  constructor() {
    this.stompClient = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      reconnectDelay: 5000,
      beforeConnect: async () => {
        await this.keycloak.updateToken(30);
        this.stompClient.connectHeaders = { Authorization: `Bearer ${this.keycloak.token}` };
      },
      onConnect: () => this.subscribeAndLoad(),
    });
    this.stompClient.activate();
  }

  ngOnDestroy(): void {
    this.stompClient.deactivate();
  }

  private subscribeAndLoad(): void {
    this.loadNotifications();
    this.subscription?.unsubscribe();
    this.subscription = this.stompClient.subscribe(
      '/user/queue/notifications',
      (msg: IMessage) => {
        const incoming = JSON.parse(msg.body) as NotificationResponse;
        this._notifications.update(prev => [incoming, ...prev]);
      }
    );
  }

  private loadNotifications(): void {
    this.http.get<NotificationResponse[]>('/api/notifications')
      .pipe(
        catchError(() => of([]))
      )
      .subscribe(items => this._notifications.set(items));
  }

  markRead(id: string): void {
    this.http.patch(`/api/notifications/${id}/read`, {})
      .pipe(
        catchError(() => of(null))
      )
      .subscribe(() => {
        this._notifications.update(prev =>
          prev.map(n => n.id === id ? { ...n, read: true } : n)
        );
      });
  }

}
```

- [ ] **Step 3: `agent-task.service.ts` — drop clientId**

Replace the full contents of `hermes-frontend/src/app/core/agent-task.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgentTaskResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class AgentTaskService {
  private readonly http = inject(HttpClient);

  getTasks(): Observable<AgentTaskResponse[]> {
    return this.http.get<AgentTaskResponse[]>('/api/agent-tasks');
  }

  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`/api/agent-tasks/${id}`);
  }
}
```

- [ ] **Step 4: `chat.service.ts` — authenticate the STOMP connection, drop clientId from the payload**

Replace the full contents of `hermes-frontend/src/app/core/chat.service.ts`:

```ts
import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import Keycloak from 'keycloak-js';
import { ChatListingCard, ResultFrame, TokenFrame } from './api.types';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  listings?: ChatListingCard[];
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly keycloak = inject(Keycloak);

  readonly sessionId: string;

  private readonly client: Client;
  private subscription?: StompSubscription;
  private readonly _messages = signal<ChatMessage[]>([]);
  private readonly _isStreaming = signal(false);
  private readonly _isOpen = signal(false);

  readonly messages = this._messages.asReadonly();
  readonly isStreaming = this._isStreaming.asReadonly();
  readonly isOpen = this._isOpen.asReadonly();

  constructor() {
    this.sessionId = localStorage.getItem('hermes-chat-session') ?? this.initSession();

    this.client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      reconnectDelay: 5000,
      beforeConnect: async () => {
        await this.keycloak.updateToken(30);
        this.client.connectHeaders = { Authorization: `Bearer ${this.keycloak.token}` };
      },
      onConnect: () => this.subscribe(),
    });

    this.client.activate();
  }

  private initSession(): string {
    const id = crypto.randomUUID();
    localStorage.setItem('hermes-chat-session', id);
    return id;
  }

  private subscribe(): void {
    this.subscription?.unsubscribe();
    this.subscription = this.client.subscribe(`/topic/chat/${this.sessionId}`, (msg: IMessage) => {
      const frame = JSON.parse(msg.body) as TokenFrame | ResultFrame;

      if (frame.type === 'TOKEN') {
        this._messages.update(msgs => {
          const last = msgs.at(-1);
          if (last?.role === 'assistant') {
            return [...msgs.slice(0, -1), { ...last, content: last.content + frame.content }];
          }
          return [...msgs, { role: 'assistant', content: frame.content }];
        });
      } else if (frame.type === 'ERROR') {
        this._isStreaming.set(false);
        this._messages.update(msgs => {
          const last = msgs.at(-1);
          if (last?.role === 'assistant') {
            return [...msgs.slice(0, -1), { ...last, content: frame.content }];
          }
          return [...msgs, { role: 'assistant', content: frame.content }];
        });
      } else if (frame.type === 'RESULT') {
        this._isStreaming.set(false);
        if (frame.listings.length > 0) {
          this._messages.update(msgs => {
            const last = msgs.at(-1);
            if (last?.role === 'assistant') {
              return [...msgs.slice(0, -1), { ...last, listings: frame.listings }];
            }
            // Tool was called but no text tokens were emitted; create a message to hold the cards
            return [...msgs, { role: 'assistant', content: '', listings: frame.listings }];
          });
        }
      }
    });
  }

  sendMessage(text: string): void {
    if (!text.trim() || this._isStreaming() || !this.client.connected) return;
    this._messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this._isStreaming.set(true);
    this.client.publish({
      destination: '/app/chat',
      body: JSON.stringify({ sessionId: this.sessionId, message: text }),
    });
  }

  toggle(): void {
    this._isOpen.update(open => !open);
  }

  seedAndOpen(assistantContent: string): void {
    this._messages.update(msgs => [...msgs, { role: 'assistant', content: assistantContent }]);
    this._isOpen.set(true);
  }
}
```

- [ ] **Step 5: Verify the frontend builds**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 6: Run the frontend test suite**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: same pass/fail counts as before this task (these four services have no existing spec files, so no new failures should appear; the same 6 pre-existing unrelated chat-component failures from earlier phases remain).

- [ ] **Step 7: Commit**

```bash
git add hermes-frontend/src/app/core/favorites.service.ts \
        hermes-frontend/src/app/core/notifications.service.ts \
        hermes-frontend/src/app/core/agent-task.service.ts \
        hermes-frontend/src/app/core/chat.service.ts
git commit -m "feat(frontend): drop clientId from every request, authenticate the chat WebSocket"
```

---

## Task 7: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Confirm the migrations applied and tables are truly identity-owned**

```bash
docker logs $(docker compose ps -q backend) 2>&1 | grep -i "V12\|V13"
```

Expected: log lines showing both migrations applied successfully.

- [ ] **Step 3: Confirm an unauthenticated WebSocket CONNECT is rejected**

Open browser devtools on `http://localhost:4200` before logging in (or use an incognito window), and check the Network/WS tab: the STOMP connection to `/ws/chat` should fail or never establish while unauthenticated (the app's route guard should redirect to Keycloak login before any WebSocket connection is attempted anyway — confirm this is still true).

- [ ] **Step 4: Verify per-user data isolation**

Log in as `testuser` / `password`:
- Add a favorite listing; confirm it appears in the favorites list.
- Send a chat message asking to "save a watch for 3-bed houses in Utrecht under 400k"; confirm the watch appears when asking "what alerts do I have".
- Confirm a chat response streams normally (proves the authenticated STOMP connection works end-to-end).

Log out, log in as `testadmin` / `password`:
- Confirm `testadmin` sees **no** favorites and **no** watches (proving isolation — this data belongs to `testuser`, not `testadmin`).

- [ ] **Step 5: Verify live notification delivery over /user/queue**

While logged in as `testuser` with the app open, trigger an agent task that produces a notification (e.g. wait for a scheduled watch to run, or use the research tool via chat: "research the Amsterdam market and tell me what you find" — this creates a `RESEARCH` task with `nextRunAt = now`, which the scheduler picks up on its next poll). Confirm the notification bell updates in real time without a page reload, proving `/user/queue/notifications` delivery works.

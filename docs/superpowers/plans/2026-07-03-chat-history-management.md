# Chat History Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users start new chat conversations, list their past ones, switch between them, and delete ones they no longer want.

**Architecture:** No new table — a "conversation" is the set of existing `chat_messages` rows sharing one `session_id` for one `user_id`. New backend REST endpoints (list/get-messages/delete) derive everything from that table via a grouped native query. The frontend's `ChatService` gains session-switching logic on top of its existing STOMP client, and a new history panel component lists/switches/deletes conversations.

**Tech Stack:** Spring Boot 4.0.6, Spring Data JPA (native queries + interface projections), MapStruct, OpenAPI Generator (interface-only), Angular 22, RxJS `HttpClient`.

## Global Constraints

- Owner-only — no admin override for viewing/deleting other users' conversations (chat content is more sensitive than watches/notifications; explicit user decision).
- No new database table or migration — everything is derived from the existing `chat_messages` table.
- Cross-user session ids are never a 403/404 — because every query/mutation here is scoped by `(session_id, user_id)` together at the database level, requesting or deleting a `session_id` that belongs to someone else silently returns an empty list / a no-op delete (200/204), not an error. Do not add an ownership-check-then-403 mechanism here — that pattern applies to phase 4's bare-id lookups, not this.
- No renaming/editing of conversation titles — titles are derived from the first user message, not stored or user-editable.
- Conversation list is capped at the 50 most recent (same shape as the existing `findTop50ByUserIdOrderByCreatedAtDesc` notifications query) — no further pagination.
- A conversation with zero messages never appears in the list (it doesn't exist in the database until the first message is saved) — "New chat" is a frontend-only action needing no backend call.

---

## Task 1: Repository layer — grouped session listing + scoped delete

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java`

**Interfaces:**
- Produces: `ChatSessionProjection` (interface with `getSessionId(): UUID`, `getLastMessageAt(): Instant`, `getTitleSource(): String`), `ChatMessageRepository.findSessionSummariesByUserId(UUID userId): List<ChatSessionProjection>`, `ChatMessageRepository.deleteBySessionIdAndUserId(UUID sessionId, UUID userId): void`.

- [ ] **Step 1: Write the failing repository test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ChatMessageRepositoryTest.Containers.class)
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ChatMessageRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            );
        }
    }

    @Autowired ChatMessageRepository chatMessageRepository;

    private ChatMessageEntity saveMessage(UUID sessionId, UUID userId, String role, String content) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setSessionId(sessionId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        return chatMessageRepository.saveAndFlush(m);
    }

    @Test
    void findSessionSummariesByUserId_groupsBySessionAndUsesFirstUserMessageAsTitleSource() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, userId, "USER", "First message in this conversation");
        saveMessage(sessionId, userId, "ASSISTANT", "A reply");
        saveMessage(sessionId, userId, "USER", "A follow-up question");

        List<ChatSessionProjection> result = chatMessageRepository.findSessionSummariesByUserId(userId);

        assertThat(result).hasSize(1);
        ChatSessionProjection summary = result.get(0);
        assertThat(summary.getSessionId()).isEqualTo(sessionId);
        assertThat(summary.getTitleSource()).isEqualTo("First message in this conversation");
    }

    @Test
    void findSessionSummariesByUserId_multipleSessionsOrderedByMostRecentFirst() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID olderSession = UUID.randomUUID();
        UUID newerSession = UUID.randomUUID();
        saveMessage(olderSession, userId, "USER", "Older conversation");
        Thread.sleep(10); // ensure a strictly later created_at for the newer session
        saveMessage(newerSession, userId, "USER", "Newer conversation");

        List<ChatSessionProjection> result = chatMessageRepository.findSessionSummariesByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSessionId()).isEqualTo(newerSession);
        assertThat(result.get(1).getSessionId()).isEqualTo(olderSession);
    }

    @Test
    void findSessionSummariesByUserId_onlyReturnsCallersOwnSessions() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        saveMessage(UUID.randomUUID(), otherUserId, "USER", "Someone else's conversation");

        List<ChatSessionProjection> result = chatMessageRepository.findSessionSummariesByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteBySessionIdAndUserId_removesOnlyMatchingRows() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, userId, "USER", "Delete me");
        saveMessage(sessionId, userId, "ASSISTANT", "Delete me too");
        saveMessage(UUID.randomUUID(), otherUserId, "USER", "Untouched, different user");

        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);

        assertThat(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)).isEmpty();
        assertThat(chatMessageRepository.findSessionSummariesByUserId(otherUserId)).hasSize(1);
    }

    @Test
    void deleteBySessionIdAndUserId_wrongUserIsNoOp() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, ownerId, "USER", "Not yours to delete");

        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, callerId);

        assertThat(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, ownerId)).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ChatMessageRepositoryTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `ChatSessionProjection` and the two new repository methods don't exist yet.

- [ ] **Step 3: Create `ChatSessionProjection`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public interface ChatSessionProjection {
    UUID getSessionId();
    Instant getLastMessageAt();
    String getTitleSource();
}
```

- [ ] **Step 4: Add the two repository methods**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID sessionId, UUID userId);

    @Query(value = """
            SELECT
              m.session_id AS sessionId,
              MAX(m.created_at) AS lastMessageAt,
              (SELECT m2.content FROM chat_messages m2
               WHERE m2.session_id = m.session_id AND m2.user_id = m.user_id AND m2.role = 'USER'
               ORDER BY m2.created_at ASC LIMIT 1) AS titleSource
            FROM chat_messages m
            WHERE m.user_id = :userId
            GROUP BY m.session_id, m.user_id
            ORDER BY MAX(m.created_at) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<ChatSessionProjection> findSessionSummariesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM chat_messages WHERE session_id = :sessionId AND user_id = :userId", nativeQuery = true)
    void deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ChatMessageRepositoryTest -f hermes-backend/pom.xml`
Expected: PASS, 5 tests, 0 failures.

- [ ] **Step 6: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java
git commit -m "feat(backend): add grouped session-listing query and scoped delete to ChatMessageRepository"
```

---

## Task 2: Service, OpenAPI spec, controller, and mapper

**Files:**
- Create: `hermes-backend/src/main/resources/openapi/chat-history.yaml`
- Modify: `hermes-backend/pom.xml`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionSummaryDto.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageDto.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryApiMapper.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryController.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java`

**Interfaces:**
- Consumes: `ChatMessageRepository.findSessionSummariesByUserId(UUID)`, `.findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID, UUID)`, `.deleteBySessionIdAndUserId(UUID, UUID)` (Task 1). `CurrentUser.current().id()` (established pattern from phases 1-4).
- Produces: `ChatSessionSummaryDto(UUID sessionId, String title, Instant lastMessageAt)`, `ChatMessageDto(String role, String content, Instant createdAt)`, `ChatHistoryService.listSessions(UUID userId): List<ChatSessionSummaryDto>`, `.getMessages(UUID userId, UUID sessionId): List<ChatMessageDto>`, `.deleteSession(UUID userId, UUID sessionId): void`. Generated OpenAPI types `ChatSessionSummaryResponse`/`ChatMessageResponse` in package `com.kropholler.dev.hermes.ai.chat.openapi`, consumed by the frontend as `ChatSessionSummaryResponse`/`ChatMessageResponse` TypeScript interfaces (Task 3).

- [ ] **Step 1: Add the OpenAPI spec**

Create `hermes-backend/src/main/resources/openapi/chat-history.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Hermes Chat History API
  version: 1.0.0

paths:
  /api/chat/sessions:
    get:
      tags: [ChatHistory]
      operationId: getChatSessions
      responses:
        '200':
          description: The caller's conversations, most recent first
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/ChatSessionSummaryResponse'

  /api/chat/sessions/{sessionId}/messages:
    get:
      tags: [ChatHistory]
      operationId: getChatSessionMessages
      parameters:
        - name: sessionId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Full transcript for one conversation, oldest first
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/ChatMessageResponse'

  /api/chat/sessions/{sessionId}:
    delete:
      tags: [ChatHistory]
      operationId: deleteChatSession
      parameters:
        - name: sessionId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Deleted (or a no-op if the session didn't belong to the caller)

components:
  schemas:
    ChatSessionSummaryResponse:
      type: object
      properties:
        sessionId:
          type: string
          format: uuid
        title:
          type: string
        lastMessageAt:
          type: string
          format: date-time

    ChatMessageResponse:
      type: object
      properties:
        role:
          type: string
        content:
          type: string
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

- [ ] **Step 2: Wire the spec into the OpenAPI codegen plugin**

In `hermes-backend/pom.xml`, inside the `openapi-generator-maven-plugin`'s `<executions>` block, add a new `<execution>` alongside the existing `generate-notifications`/`generate-agent-tasks` ones:

```xml
                    <execution>
                        <id>generate-chat-history</id>
                        <goals><goal>generate</goal></goals>
                        <configuration>
                            <inputSpec>${project.basedir}/src/main/resources/openapi/chat-history.yaml</inputSpec>
                            <generatorName>spring</generatorName>
                            <apiPackage>com.kropholler.dev.hermes.ai.chat.openapi</apiPackage>
                            <modelPackage>com.kropholler.dev.hermes.ai.chat.openapi</modelPackage>
                            <schemaMappings><schemaMapping>ProblemDetail=org.springframework.http.ProblemDetail</schemaMapping></schemaMappings>
                            <configOptions>
                                <interfaceOnly>true</interfaceOnly>
                                <useSpringBoot3>true</useSpringBoot3>
                                <useTags>true</useTags>
                                <openApiNullable>false</openApiNullable>
                                <documentationProvider>none</documentationProvider>
                                <useJakartaEe>true</useJakartaEe>
                            </configOptions>
                        </configuration>
                    </execution>
```

- [ ] **Step 3: Generate sources and confirm the interface compiles**

Run: `mvn generate-sources -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS, and `hermes-backend/target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/ai/chat/openapi/ChatHistoryApi.java` exists with methods `getChatSessions()`, `getChatSessionMessages(UUID sessionId)`, `deleteChatSession(UUID sessionId)`.

- [ ] **Step 4: Create the DTOs**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionSummaryDto.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionSummaryDto(UUID sessionId, String title, Instant lastMessageAt) {}
```

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageDto.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;

public record ChatMessageDto(String role, String content, Instant createdAt) {}
```

- [ ] **Step 5: Write the failing service test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock ChatMessageRepository chatMessageRepository;
    @InjectMocks ChatHistoryService service;

    private ChatSessionProjection projection(UUID sessionId, String titleSource, Instant lastMessageAt) {
        return new ChatSessionProjection() {
            @Override public UUID getSessionId() { return sessionId; }
            @Override public Instant getLastMessageAt() { return lastMessageAt; }
            @Override public String getTitleSource() { return titleSource; }
        };
    }

    @Test
    void listSessions_mapsProjectionsToDtosWithTruncatedTitle() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        String longMessage = "x".repeat(80);
        when(chatMessageRepository.findSessionSummariesByUserId(userId))
            .thenReturn(List.of(projection(sessionId, longMessage, lastMessageAt)));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sessionId()).isEqualTo(sessionId);
        assertThat(result.get(0).lastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(result.get(0).title()).hasSize(61); // 60 chars + ellipsis
        assertThat(result.get(0).title()).endsWith("…");
    }

    @Test
    void listSessions_shortTitleSourceIsNotTruncated() {
        UUID userId = UUID.randomUUID();
        when(chatMessageRepository.findSessionSummariesByUserId(userId))
            .thenReturn(List.of(projection(UUID.randomUUID(), "Short message", Instant.now())));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEqualTo("Short message");
    }

    @Test
    void getMessages_mapsEntitiesToDtos() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setRole("USER");
        entity.setContent("Hello");
        when(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId))
            .thenReturn(List.of(entity));

        List<ChatMessageDto> result = service.getMessages(userId, sessionId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(0).content()).isEqualTo("Hello");
    }

    @Test
    void deleteSession_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        service.deleteSession(userId, sessionId);

        verify(chatMessageRepository).deleteBySessionIdAndUserId(sessionId, userId);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn test -Dtest=ChatHistoryServiceTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `ChatHistoryService` does not exist.

- [ ] **Step 7: Write `ChatHistoryService`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final int MAX_TITLE_LENGTH = 60;

    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSessionSummaryDto> listSessions(UUID userId) {
        return chatMessageRepository.findSessionSummariesByUserId(userId).stream()
            .map(p -> new ChatSessionSummaryDto(p.getSessionId(), truncateTitle(p.getTitleSource()), p.getLastMessageAt()))
            .toList();
    }

    public List<ChatMessageDto> getMessages(UUID userId, UUID sessionId) {
        return chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId).stream()
            .map(e -> new ChatMessageDto(e.getRole(), e.getContent(), e.getCreatedAt()))
            .toList();
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);
    }

    private static String truncateTitle(String titleSource) {
        if (titleSource == null) {
            return "";
        }
        String trimmed = titleSource.strip();
        return trimmed.length() <= MAX_TITLE_LENGTH ? trimmed : trimmed.substring(0, MAX_TITLE_LENGTH) + "…";
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn test -Dtest=ChatHistoryServiceTest -f hermes-backend/pom.xml`
Expected: PASS, 4 tests, 0 failures.

- [ ] **Step 9: Write the mapper**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryApiMapper.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.openapi.ChatMessageResponse;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatSessionSummaryResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
interface ChatHistoryApiMapper {

    ChatSessionSummaryResponse toResponse(ChatSessionSummaryDto dto);

    ChatMessageResponse toResponse(ChatMessageDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
```

- [ ] **Step 10: Write the failing controller test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatHistoryController.class)
@Import(SecurityConfig.class)
class ChatHistoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ChatHistoryService chatHistoryService;

    @Test
    void getChatSessions_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        when(chatHistoryService.listSessions(userId))
            .thenReturn(List.of(new ChatSessionSummaryDto(sessionId, "Hello there", lastMessageAt)));

        mockMvc.perform(get("/api/chat/sessions")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessionId").value(sessionId.toString()))
            .andExpect(jsonPath("$[0].title").value("Hello there"));
    }

    @Test
    void getChatSessionMessages_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(chatHistoryService.getMessages(userId, sessionId))
            .thenReturn(List.of(new ChatMessageDto("USER", "Hi", Instant.parse("2026-06-01T08:00:00Z"))));

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", sessionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").value("USER"))
            .andExpect(jsonPath("$[0].content").value("Hi"));
    }

    @Test
    void deleteChatSession_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/chat/sessions/{sessionId}", sessionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(chatHistoryService).deleteSession(eq(userId), eq(sessionId));
    }
}
```

- [ ] **Step 11: Write `ChatHistoryController`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryController.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.openapi.ChatHistoryApi;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatMessageResponse;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatSessionSummaryResponse;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatHistoryController implements ChatHistoryApi {

    private final ChatHistoryService chatHistoryService;
    private final ChatHistoryApiMapper chatHistoryApiMapper;

    @Override
    public ResponseEntity<List<ChatSessionSummaryResponse>> getChatSessions() {
        List<ChatSessionSummaryResponse> responses = chatHistoryService.listSessions(CurrentUser.current().id())
            .stream().map(chatHistoryApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<List<ChatMessageResponse>> getChatSessionMessages(UUID sessionId) {
        List<ChatMessageResponse> responses = chatHistoryService.getMessages(CurrentUser.current().id(), sessionId)
            .stream().map(chatHistoryApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteChatSession(UUID sessionId) {
        chatHistoryService.deleteSession(CurrentUser.current().id(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 12: Run the new tests**

Run: `mvn test -Dtest=ChatHistoryServiceTest,ChatHistoryControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 7 tests, 0 failures.

- [ ] **Step 13: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 14: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/chat-history.yaml \
        hermes-backend/pom.xml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionSummaryDto.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageDto.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryApiMapper.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java
git commit -m "feat(backend): add chat history REST endpoints (list, get messages, delete)"
```

---

## Task 3: Frontend — `ChatService` session switching

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`
- Modify: `hermes-frontend/src/app/core/chat.service.ts`
- Test: `hermes-frontend/src/app/core/chat.service.spec.ts`

**Interfaces:**
- Consumes: `GET /api/chat/sessions`, `GET /api/chat/sessions/{sessionId}/messages`, `DELETE /api/chat/sessions/{sessionId}` (Task 2).
- Produces: `ChatService.sessions: Signal<ChatSessionSummaryResponse[]>`, `.loadSessions(): void`, `.switchSession(sessionId: string): void`, `.startNewChat(): void`, `.deleteSession(sessionId: string): void`, `.sessionId: string` (now a getter over an internal mutable field, was a `readonly` field before — same public shape). Consumed by the new history panel component (Task 4).

**Note on test scope:** this codebase has no existing tests for STOMP-based services (`ChatService`/`NotificationsService` both currently untested) — real WebSocket connection/subscription behavior isn't practical to unit test without deep STOMP mocking, and Task 5's manual E2E covers the real resubscription behavior end-to-end. These tests cover only the HTTP-driven and pure-state parts: `loadSessions`, `switchSession`'s message-fetch and state changes, `startNewChat`, and `deleteSession`. Constructing a real `ChatService` in tests is safe — `Client.activate()` attempts a WebSocket connection asynchronously and fails silently in the test environment (connection refused), which doesn't throw or block the test.

- [ ] **Step 1: Add the response types**

In `hermes-frontend/src/app/core/api.types.ts`, add:

```ts
export interface ChatSessionSummaryResponse {
  sessionId: string;
  title: string;
  lastMessageAt: string;
}

export interface ChatMessageResponse {
  role: string;
  content: string;
  createdAt: string;
}
```

- [ ] **Step 2: Write the failing tests**

Create `hermes-frontend/src/app/core/chat.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { ChatService } from './chat.service';
import { ChatSessionSummaryResponse, ChatMessageResponse } from './api.types';

describe('ChatService', () => {
  let service: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('hermes-chat-session');

    const keycloakStub = {
      token: 'fake-token',
      updateToken: jasmine.createSpy('updateToken').and.resolveTo(false),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('hermes-chat-session');
  });

  it('loadSessions populates the sessions signal', () => {
    const response: ChatSessionSummaryResponse[] = [
      { sessionId: 'abc', title: 'Hello', lastMessageAt: '2026-06-01T08:00:00Z' },
    ];

    service.loadSessions();
    const req = httpMock.expectOne('/api/chat/sessions');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.sessions()).toEqual(response);
  });

  it('loadSessions sets an empty list on error', () => {
    service.loadSessions();
    const req = httpMock.expectOne('/api/chat/sessions');
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(service.sessions()).toEqual([]);
  });

  it('switchSession updates sessionId, persists it, and loads messages', () => {
    const originalSessionId = service.sessionId;
    const targetSessionId = 'target-session-id';
    const history: ChatMessageResponse[] = [
      { role: 'USER', content: 'Hi', createdAt: '2026-06-01T08:00:00Z' },
      { role: 'ASSISTANT', content: 'Hello!', createdAt: '2026-06-01T08:00:01Z' },
    ];

    service.switchSession(targetSessionId);

    expect(service.sessionId).toBe(targetSessionId);
    expect(service.sessionId).not.toBe(originalSessionId);
    expect(localStorage.getItem('hermes-chat-session')).toBe(targetSessionId);

    const req = httpMock.expectOne(`/api/chat/sessions/${targetSessionId}/messages`);
    expect(req.request.method).toBe('GET');
    req.flush(history);

    expect(service.messages()).toEqual([
      { role: 'user', content: 'Hi' },
      { role: 'assistant', content: 'Hello!' },
    ]);
  });

  it('switchSession to the already-active session is a no-op', () => {
    const currentSessionId = service.sessionId;

    service.switchSession(currentSessionId);

    httpMock.expectNone(`/api/chat/sessions/${currentSessionId}/messages`);
  });

  it('startNewChat generates a fresh sessionId and clears messages', () => {
    const originalSessionId = service.sessionId;

    service.startNewChat();

    expect(service.sessionId).not.toBe(originalSessionId);
    expect(localStorage.getItem('hermes-chat-session')).toBe(service.sessionId);
    expect(service.messages()).toEqual([]);
  });

  it('deleteSession removes the session from the list', () => {
    service.loadSessions();
    httpMock.expectOne('/api/chat/sessions').flush([
      { sessionId: 'to-delete', title: 'Bye', lastMessageAt: '2026-06-01T08:00:00Z' },
    ]);

    service.deleteSession('to-delete');
    const req = httpMock.expectOne('/api/chat/sessions/to-delete');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(service.sessions()).toEqual([]);
  });

  it('deleteSession starts a new chat if the deleted session was active', () => {
    const activeSessionId = service.sessionId;

    service.deleteSession(activeSessionId);
    const req = httpMock.expectOne(`/api/chat/sessions/${activeSessionId}`);
    req.flush(null);

    expect(service.sessionId).not.toBe(activeSessionId);
    expect(service.messages()).toEqual([]);
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `chat.service.spec.ts` references methods (`loadSessions`, `switchSession`, `startNewChat`, `deleteSession`, `sessions`) that don't exist on `ChatService` yet.

- [ ] **Step 4: Update `ChatService`**

Replace the full contents of `hermes-frontend/src/app/core/chat.service.ts`:

```ts
import { Injectable, inject, signal, effect } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { catchError, of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { ChatListingCard, ChatMessageResponse, ChatSessionSummaryResponse, ResultFrame, TokenFrame } from './api.types';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  listings?: ChatListingCard[];
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly keycloak = inject(Keycloak);
  private readonly http = inject(HttpClient);

  private _sessionId: string;
  get sessionId(): string {
    return this._sessionId;
  }

  private readonly client: Client;
  private subscription?: StompSubscription;
  private readonly _messages = signal<ChatMessage[]>([]);
  private readonly _isStreaming = signal(false);
  private readonly _isOpen = signal(false);
  private readonly _sessions = signal<ChatSessionSummaryResponse[]>([]);
  private isFreshConversation = true;

  readonly messages = this._messages.asReadonly();
  readonly isStreaming = this._isStreaming.asReadonly();
  readonly isOpen = this._isOpen.asReadonly();
  readonly sessions = this._sessions.asReadonly();

  constructor() {
    this._sessionId = localStorage.getItem('hermes-chat-session') ?? this.generateSessionId();

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

    effect(() => {
      const streaming = this._isStreaming();
      if (!streaming && this.isFreshConversation && this._messages().length > 0) {
        this.isFreshConversation = false;
        this.loadSessions();
      }
    });
  }

  private generateSessionId(): string {
    const id = crypto.randomUUID();
    localStorage.setItem('hermes-chat-session', id);
    return id;
  }

  private subscribe(): void {
    this.subscription?.unsubscribe();
    this.subscription = this.client.subscribe(`/topic/chat/${this._sessionId}`, (msg: IMessage) => {
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
      body: JSON.stringify({ sessionId: this._sessionId, message: text }),
    });
  }

  toggle(): void {
    this._isOpen.update(open => !open);
  }

  seedAndOpen(assistantContent: string): void {
    this._messages.update(msgs => [...msgs, { role: 'assistant', content: assistantContent }]);
    this._isOpen.set(true);
  }

  loadSessions(): void {
    this.http.get<ChatSessionSummaryResponse[]>('/api/chat/sessions')
      .pipe(catchError(() => of([])))
      .subscribe(items => this._sessions.set(items));
  }

  switchSession(sessionId: string): void {
    if (sessionId === this._sessionId) return;
    this.subscription?.unsubscribe();
    this._sessionId = sessionId;
    localStorage.setItem('hermes-chat-session', sessionId);
    this.isFreshConversation = false;
    this._messages.set([]);
    this.http.get<ChatMessageResponse[]>(`/api/chat/sessions/${sessionId}/messages`)
      .pipe(catchError(() => of([])))
      .subscribe(items => {
        this._messages.set(items.map(m => ({
          role: m.role.toUpperCase() === 'USER' ? 'user' as const : 'assistant' as const,
          content: m.content,
        })));
      });
    if (this.client.connected) this.subscribe();
  }

  startNewChat(): void {
    this.subscription?.unsubscribe();
    this._sessionId = this.generateSessionId();
    this.isFreshConversation = true;
    this._messages.set([]);
    if (this.client.connected) this.subscribe();
  }

  deleteSession(sessionId: string): void {
    this.http.delete(`/api/chat/sessions/${sessionId}`)
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this._sessions.update(list => list.filter(s => s.sessionId !== sessionId));
        if (sessionId === this._sessionId) {
          this.startNewChat();
        }
      });
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `ChatService`'s 7 new tests pass (pre-existing unrelated chat-component failures unchanged).

- [ ] **Step 6: Run the frontend build**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 7: Commit**

```bash
git add hermes-frontend/src/app/core/api.types.ts \
        hermes-frontend/src/app/core/chat.service.ts \
        hermes-frontend/src/app/core/chat.service.spec.ts
git commit -m "feat(frontend): add session listing, switching, and deletion to ChatService"
```

---

## Task 4: Frontend — history panel component and wiring

**Files:**
- Create: `hermes-frontend/src/app/shared/chat-history-panel.component.ts`
- Create: `hermes-frontend/src/app/shared/chat-history-panel.component.html`
- Test: `hermes-frontend/src/app/shared/chat-history-panel.component.spec.ts`
- Modify: `hermes-frontend/src/app/shared/chat-panel.component.ts`
- Modify: `hermes-frontend/src/app/shared/chat-panel.component.html`

**Interfaces:**
- Consumes: `ChatService.sessions`, `.sessionId`, `.loadSessions()`, `.switchSession(sessionId)`, `.startNewChat()`, `.deleteSession(sessionId)` (Task 3).

- [ ] **Step 1: Write the failing component test**

Create `hermes-frontend/src/app/shared/chat-history-panel.component.spec.ts`:

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ChatHistoryPanelComponent } from './chat-history-panel.component';
import { ChatService } from '../core/chat.service';
import { ChatSessionSummaryResponse } from '../core/api.types';

describe('ChatHistoryPanelComponent', () => {
  let fixture: ComponentFixture<ChatHistoryPanelComponent>;
  let el: HTMLElement;
  let chatSvc: jasmine.SpyObj<ChatService>;

  async function setup(sessions: ChatSessionSummaryResponse[] = [], activeSessionId = 'current-session') {
    chatSvc = jasmine.createSpyObj(
      'ChatService',
      ['loadSessions', 'switchSession', 'startNewChat', 'deleteSession'],
      {
        sessions: signal(sessions).asReadonly(),
        sessionId: activeSessionId,
      }
    );

    await TestBed.configureTestingModule({
      imports: [ChatHistoryPanelComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ChatService, useValue: chatSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatHistoryPanelComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('calls loadSessions on init', async () => {
    await setup();
    expect(chatSvc.loadSessions).toHaveBeenCalled();
  });

  it('renders a title for each session', async () => {
    await setup([
      { sessionId: 'a', title: 'First conversation', lastMessageAt: '2026-06-01T08:00:00Z' },
      { sessionId: 'b', title: 'Second conversation', lastMessageAt: '2026-06-02T08:00:00Z' },
    ]);
    expect(el.textContent).toContain('First conversation');
    expect(el.textContent).toContain('Second conversation');
  });

  it('clicking a conversation calls switchSession with its sessionId', async () => {
    await setup([{ sessionId: 'target', title: 'Click me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    el.querySelector<HTMLButtonElement>('[data-session-id="target"]')!.click();

    expect(chatSvc.switchSession).toHaveBeenCalledWith('target');
  });

  it('clicking delete calls deleteSession and does not also switch', async () => {
    await setup([{ sessionId: 'target', title: 'Delete me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    el.querySelector<HTMLButtonElement>('[data-delete-session-id="target"]')!.click();

    expect(chatSvc.deleteSession).toHaveBeenCalledWith('target');
    expect(chatSvc.switchSession).not.toHaveBeenCalled();
  });

  it('clicking New chat calls startNewChat', async () => {
    await setup();

    el.querySelector<HTMLButtonElement>('[data-new-chat]')!.click();

    expect(chatSvc.startNewChat).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `chat-history-panel.component.ts` does not exist yet (compilation error for this spec file).

- [ ] **Step 3: Write `ChatHistoryPanelComponent`**

Create `hermes-frontend/src/app/shared/chat-history-panel.component.ts`:

```ts
import { Component, OnInit, inject } from '@angular/core';
import { ChatService } from '../core/chat.service';

@Component({
  selector: 'app-chat-history-panel',
  standalone: true,
  templateUrl: './chat-history-panel.component.html',
})
export class ChatHistoryPanelComponent implements OnInit {
  protected readonly svc = inject(ChatService);

  ngOnInit(): void {
    this.svc.loadSessions();
  }

  protected switchTo(sessionId: string): void {
    this.svc.switchSession(sessionId);
  }

  protected deleteConversation(sessionId: string, event: Event): void {
    event.stopPropagation();
    this.svc.deleteSession(sessionId);
  }

  protected newChat(): void {
    this.svc.startNewChat();
  }
}
```

Create `hermes-frontend/src/app/shared/chat-history-panel.component.html`:

```html
<div class="flex flex-col gap-1 p-2 max-h-64 overflow-y-auto bg-slate-900 border-b border-slate-800">
  <button
    data-new-chat
    (click)="newChat()"
    class="text-left text-sm font-semibold text-cyan-400 hover:text-cyan-300 px-2 py-1.5 rounded-md hover:bg-slate-800 transition-colors"
  >
    + New chat
  </button>

  @for (session of svc.sessions(); track session.sessionId) {
    <button
      [attr.data-session-id]="session.sessionId"
      (click)="switchTo(session.sessionId)"
      [class]="session.sessionId === svc.sessionId
        ? 'flex items-center justify-between gap-2 text-left text-sm px-2 py-1.5 rounded-md bg-slate-700 text-white'
        : 'flex items-center justify-between gap-2 text-left text-sm px-2 py-1.5 rounded-md text-slate-300 hover:bg-slate-800 transition-colors'"
    >
      <span class="truncate">{{ session.title }}</span>
      <span
        [attr.data-delete-session-id]="session.sessionId"
        (click)="deleteConversation(session.sessionId, $event)"
        aria-label="Delete conversation"
        class="text-slate-500 hover:text-red-400 shrink-0 leading-none"
      >
        &times;
      </span>
    </button>
  }
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `ChatHistoryPanelComponent`'s 5 tests pass (pre-existing unrelated chat-component failures unchanged).

- [ ] **Step 5: Wire the panel into `ChatPanelComponent`**

In `hermes-frontend/src/app/shared/chat-panel.component.ts`, add the import and register the component:

```ts
import { Component, ElementRef, ViewChild, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EuroPricePipe } from './euro-price.pipe';
import { ChatHistoryPanelComponent } from './chat-history-panel.component';
import { ChatService } from '../core/chat.service';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, EuroPricePipe, ChatHistoryPanelComponent],
  templateUrl: './chat-panel.component.html',
})
export class ChatPanelComponent {
  protected readonly svc = inject(ChatService);
  protected inputText = '';
  protected showHistory = false;

  @ViewChild('messageContainer') private messageContainer!: ElementRef<HTMLElement>;

  constructor() {
    effect(() => {
      this.svc.messages();
      queueMicrotask(() => {
        const el = this.messageContainer?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    });
  }

  protected send(): void {
    this.svc.sendMessage(this.inputText);
    this.inputText = '';
  }

  protected toggleHistory(): void {
    this.showHistory = !this.showHistory;
  }
}
```

In `hermes-frontend/src/app/shared/chat-panel.component.html`, add a history-toggle button to the header (between the "Hermes AI" label and the close button) and render the panel conditionally right below the header:

```html
<div class="w-80 flex flex-col rounded-2xl overflow-hidden shadow-2xl bg-slate-900" style="height: 30rem;">

  <!-- Header -->
  <div class="flex items-center justify-between px-4 py-3 bg-slate-900 border-b border-slate-800">
    <div class="flex items-center gap-2">
      <span class="text-white font-extrabold text-base">
        <span class="text-cyan-400">H</span>ermes
      </span>
      <span class="bg-cyan-400 text-slate-900 text-[9px] font-bold tracking-widest uppercase px-1.5 py-0.5 rounded-full">AI</span>
    </div>
    <div class="flex items-center gap-3">
      <button
        (click)="toggleHistory()"
        aria-label="Toggle chat history"
        class="text-slate-500 hover:text-slate-300 transition-colors leading-none focus:outline-none text-xs font-semibold"
      >
        History
      </button>
      <button
        (click)="svc.toggle()"
        aria-label="Close chat"
        class="text-slate-500 hover:text-slate-300 transition-colors leading-none focus:outline-none"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
        </svg>
      </button>
    </div>
  </div>

  @if (showHistory) {
    <app-chat-history-panel />
  }

  <!-- Messages -->
  <div
    #messageContainer
    class="flex-1 overflow-y-auto p-3 flex flex-col gap-2.5 bg-slate-800"
    aria-live="polite"
  >
    @for (msg of svc.messages(); track $index) {
      @if (msg.content) {
        <div [class]="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
          <span
            [class]="msg.role === 'user'
              ? 'inline-block bg-cyan-400 text-slate-900 font-medium rounded-2xl rounded-br-sm px-3 py-2 text-sm max-w-[14rem] break-words'
              : 'inline-block bg-slate-700 text-slate-200 rounded-2xl rounded-bl-sm px-3 py-2 text-sm max-w-[14rem] break-words'"
          >
            {{ msg.content }}
            @if (msg.role === 'assistant' && svc.isStreaming() && $last) {
              <span class="animate-pulse">|</span>
            }
          </span>
        </div>
      }
      @if (msg.listings && msg.listings.length > 0) {
        <div class="space-y-2">
          @for (listing of msg.listings; track listing.id) {
            <a [routerLink]="['/listings', listing.id]" class="block hover:opacity-80 transition-opacity">
              <div class="bg-slate-700 rounded-xl p-3 border border-slate-600 hover:border-cyan-400 transition-colors">
                <p class="text-sm font-semibold text-slate-100 leading-snug">
                  {{ (listing.street ?? '') + ' ' + (listing.houseNumber ?? '') + (listing.houseNumberAddition ?? '') + ', ' + (listing.city ?? '') }}
                </p>
                <p class="text-sm font-bold text-cyan-400 mt-1">
                  {{ listing.currentPrice | euroPrice }}
                </p>
                @if (listing.bedrooms) {
                  <p class="text-xs text-slate-400 mt-0.5">{{ listing.bedrooms }} bedrooms @if (listing.livingAreaM2) { &middot; {{ listing.livingAreaM2 }} m&sup2; }</p>
                }
              </div>
            </a>
          }
        </div>
      }
    }
  </div>

  <!-- Input -->
  <div class="flex items-center gap-2 px-3 py-3 bg-slate-900 border-t border-slate-800">
    <input
      [(ngModel)]="inputText"
      (keydown.enter)="send()"
      [disabled]="svc.isStreaming()"
      placeholder="Ask about properties..."
      aria-label="Ask about properties"
      class="flex-1 bg-slate-800 text-slate-200 placeholder-slate-500 border border-slate-700 rounded-full px-4 py-2 text-sm focus:outline-none focus:border-cyan-400 disabled:opacity-50 transition-colors"
    />
    <button
      (click)="send()"
      [disabled]="svc.isStreaming()"
      class="bg-cyan-400 text-slate-900 font-bold rounded-full px-4 py-2 text-sm hover:bg-cyan-300 disabled:opacity-40 transition-colors whitespace-nowrap"
    >
      Send
    </button>
  </div>

</div>
```

- [ ] **Step 6: Run the full frontend test suite**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: all new tests pass; total pass count increases over the prior baseline by 12 (7 from Task 3 + 5 from this task), with the same pre-existing unrelated chat-component failures unchanged.

- [ ] **Step 7: Run the frontend build**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 8: Commit**

```bash
git add hermes-frontend/src/app/shared/chat-history-panel.component.ts \
        hermes-frontend/src/app/shared/chat-history-panel.component.html \
        hermes-frontend/src/app/shared/chat-history-panel.component.spec.ts \
        hermes-frontend/src/app/shared/chat-panel.component.ts \
        hermes-frontend/src/app/shared/chat-panel.component.html
git commit -m "feat(frontend): add chat history panel with New chat, switch, and delete"
```

---

## Task 5: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Create two separate conversations**

Log in as `testuser` / `password`. Open the chat, send a message (e.g. "hi, tell me about houses in Utrecht"), wait for the reply to finish streaming. Click "History" — confirm the conversation now appears in the list with a title derived from the first message. Click "+ New chat" — confirm the message pane clears. Send a different message (e.g. "what's the average price in Amsterdam?"), wait for it to finish. Open History again — confirm both conversations are listed, most recent first.

- [ ] **Step 3: Switch between conversations**

Click the older conversation in the history list — confirm its original transcript loads correctly (not mixed with the newer one), and sending a new message in this conversation continues that thread (check the browser's network tab or backend logs to confirm the STOMP subscription is now on this session's topic, not the other one). Switch back to the newer conversation and confirm the same.

- [ ] **Step 4: Delete a conversation**

While viewing the newer (active) conversation, click its delete (×) button in the history list. Confirm: the conversation disappears from the list, and a fresh empty "New chat" starts automatically (matching the "auto-start a new conversation" decision). Then delete the older, non-active conversation from the list — confirm it disappears without affecting the currently active (new) conversation.

- [ ] **Step 5: Confirm per-user isolation**

Log in as `testadmin` / `password` (a different browser profile or incognito window to keep both sessions active, matching phase 3's "shared browser, user switch" precedent). Open History — confirm `testadmin` sees no conversations from `testuser` (empty list, or only `testadmin`'s own prior conversations if any exist). Send a message as `testadmin`, confirm it appears in `testadmin`'s own history only.

- [ ] **Step 6: Confirm no regressions**

Run the full backend and frontend test suites one more time on the final state:

```bash
mvn test -f hermes-backend/pom.xml
npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless
```

Expected: BUILD SUCCESS / same pass counts as Task 4's step 6, no new failures.

# Data Encryption for Sensitive Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Encrypt sensitive columns (chat message content, notification title/body, user profile address/email/geocode, agent task name/payload) at rest, transparently, without touching any caller code outside the entities/repositories/services listed below.

**Architecture:** A new `crypto` package provides a `FieldEncryptor` component wrapping `org.springframework.security.crypto.encrypt.Encryptors.text(key, salt)`, plus two Spring-managed JPA `AttributeConverter`s (`EncryptedStringConverter`, `EncryptedDoubleConverter`) that delegate to it. Entities apply `@Convert` per field. Two existing bypasses of JPA hydration are fixed as part of this work: the chat-session-title native query (reworked into entity-hydrating queries) and the `upsertEmail` native blind-upsert (replaced by a JPQL update + JPA insert fallback). A final Flyway migration (`V15`) truncates the four affected tables (dev data, disposable) and widens/retypes the affected columns to `TEXT`.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA / Hibernate 6, `spring-security-crypto` (already on the classpath transitively via `spring-boot-starter-oauth2-resource-server` — no new dependency), Flyway, PostgreSQL (+PostGIS) via Testcontainers for integration tests, H2 for plain `@DataJpaTest`s.

## Global Constraints

- Fields to encrypt (from spec Section 2): `ChatMessageEntity.content`; `NotificationEntity.title`, `body`; `UserProfileEntity.street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `email`, `latitude`, `longitude`; `AgentTaskEntity.name`, `payload`.
- Explicitly NOT encrypted: `NotificationEntity.listingIds`, `AgentTaskEntity.type/status/schedule`, all `listings.*` columns.
- `hermes.encryption.key` / `hermes.encryption.salt` are required properties, env-var-backed in `application.properties`, fixed literal values in test `application.properties`.
- `upsertEmail` (native `INSERT ... ON CONFLICT`) is replaced entirely by a JPQL `updateEmail` + JPA-insert-fallback pair (never re-add native SQL for this path) — see spec Section 3.
- The insert-fallback path in `UserProfileService.syncEmail` must catch `DataIntegrityViolationException` and retry as an update (race guard — spec Section 3).
- `V15__encrypt_sensitive_columns.sql` truncates `chat_messages`, `notifications`, `user_profiles`, `agent_tasks` before altering their column types — existing dev data in those tables is not preserved.
- No change to the PostGIS radius-search code path or the `listings` table.

---

### Task 1: `FieldEncryptor` + encryption properties

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/FieldEncryptor.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/FieldEncryptorTest.java`
- Modify: `hermes-backend/src/main/resources/application.properties`
- Modify: `hermes-backend/src/test/resources/application.properties`

**Interfaces:**
- Produces: `FieldEncryptor(String key, String salt)` constructor; `String encrypt(String plaintext)`; `String decrypt(String ciphertext)` — both null-safe (null in → null out). Used by every converter in Task 2.

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldEncryptorTest {

    private final FieldEncryptor encryptor = new FieldEncryptor("test-encryption-key", "abcd1234abcd1234");

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).isNotEqualTo("hello@hermes.local");
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        assertThat(encryptor.decrypt(null)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=FieldEncryptorTest`
Expected: FAIL to compile — `FieldEncryptor` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.kropholler.dev.hermes.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryptor {

    private final TextEncryptor encryptor;

    public FieldEncryptor(
            @Value("${hermes.encryption.key}") String key,
            @Value("${hermes.encryption.salt}") String salt) {
        this.encryptor = Encryptors.text(key, salt);
    }

    public String encrypt(String plaintext) {
        return plaintext == null ? null : encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return ciphertext == null ? null : encryptor.decrypt(ciphertext);
    }
}
```

- [ ] **Step 4: Add the properties**

In `hermes-backend/src/main/resources/application.properties`, append:

```properties

# Field-level encryption (see docs/superpowers/specs/2026-07-06-data-encryption-design.md)
hermes.encryption.key=${HERMES_ENCRYPTION_KEY}
hermes.encryption.salt=${HERMES_ENCRYPTION_SALT}
```

In `hermes-backend/src/test/resources/application.properties`, append:

```properties
hermes.encryption.key=test-encryption-key
hermes.encryption.salt=abcd1234abcd1234
```

(The salt must be a valid hex string per `Encryptors.text`'s contract — `abcd1234abcd1234` qualifies.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=FieldEncryptorTest`
Expected: PASS (3/3 tests green)

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/FieldEncryptor.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/FieldEncryptorTest.java \
        hermes-backend/src/main/resources/application.properties \
        hermes-backend/src/test/resources/application.properties
git commit -m "feat: add FieldEncryptor and encryption key/salt properties"
```

---

### Task 2: `EncryptedStringConverter` and `EncryptedDoubleConverter`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverter.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverter.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverterTest.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverterTest.java`

**Interfaces:**
- Consumes: `FieldEncryptor` from Task 1 (constructor-injected).
- Produces: `EncryptedStringConverter implements AttributeConverter<String, String>`; `EncryptedDoubleConverter implements AttributeConverter<Double, String>`. Both are `@Component @Converter(autoApply = false)` — referenced explicitly via `@Convert(converter = ...)` on entity fields in Tasks 3–6.

- [ ] **Step 1: Write the failing tests**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor("test-encryption-key", "abcd1234abcd1234");
    private final EncryptedStringConverter converter = new EncryptedStringConverter(fieldEncryptor);

    @Test
    void roundTrip_returnsOriginalValue() {
        String ciphertext = converter.convertToDatabaseColumn("52 Dorpstraat");

        assertThat(ciphertext).isNotEqualTo("52 Dorpstraat");
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("52 Dorpstraat");
    }

    @Test
    void nullAttribute_convertsToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullColumn_convertsToNullAttribute() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
```

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedDoubleConverterTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor("test-encryption-key", "abcd1234abcd1234");
    private final EncryptedDoubleConverter converter = new EncryptedDoubleConverter(fieldEncryptor);

    @Test
    void roundTrip_returnsOriginalValue() {
        String ciphertext = converter.convertToDatabaseColumn(52.09);

        assertThat(ciphertext).isNotEqualTo("52.09");
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo(52.09);
    }

    @Test
    void nullAttribute_convertsToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullColumn_convertsToNullAttribute() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd hermes-backend && mvn -q test -Dtest=EncryptedStringConverterTest,EncryptedDoubleConverterTest`
Expected: FAIL to compile — converter classes don't exist.

- [ ] **Step 3: Write the implementations**

```java
package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor fieldEncryptor;

    public EncryptedStringConverter(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptor.decrypt(dbData);
    }
}
```

```java
package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedDoubleConverter implements AttributeConverter<Double, String> {

    private final FieldEncryptor fieldEncryptor;

    public EncryptedDoubleConverter(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        return attribute == null ? null : fieldEncryptor.encrypt(attribute.toString());
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        String decrypted = fieldEncryptor.decrypt(dbData);
        return decrypted == null ? null : Double.valueOf(decrypted);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd hermes-backend && mvn -q test -Dtest=EncryptedStringConverterTest,EncryptedDoubleConverterTest`
Expected: PASS (6/6 tests green)

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverter.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverter.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverterTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverterTest.java
git commit -m "feat: add EncryptedStringConverter and EncryptedDoubleConverter"
```

---

### Task 3: Encrypt chat message content + rework the session-title query

This task both applies encryption to `ChatMessageEntity.content` and fixes the one native-SQL bypass this creates (spec Section 3, fix #1) — they must ship together or the chat sidebar breaks.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionOverviewProjection.java`
- Delete: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2.
- Produces: `ChatMessageRepository.findSessionOverviewsByUserId(UUID userId): List<ChatSessionOverviewProjection>`; `ChatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(UUID sessionId, UUID userId, String role): Optional<ChatMessageEntity>`. `ChatSessionProjection` and `ChatMessageRepository.findSessionSummariesByUserId` no longer exist — nothing outside this task references them.

- [ ] **Step 1: Apply the converter to `ChatMessageEntity.content`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`, add the import and annotation:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String role;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: Create the new overview projection**

```java
package com.kropholler.dev.hermes.ai.chat;

import java.time.Instant;
import java.util.UUID;

public interface ChatSessionOverviewProjection {
    UUID getSessionId();
    Instant getLastMessageAt();
}
```

- [ ] **Step 3: Delete the old ciphertext-returning projection**

```bash
git rm hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java
```

- [ ] **Step 4: Replace the native query with two entity-hydrating queries**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(UUID sessionId, UUID userId);

    @Query("""
            SELECT m.sessionId AS sessionId, MAX(m.createdAt) AS lastMessageAt
            FROM ChatMessageEntity m
            WHERE m.userId = :userId
            GROUP BY m.sessionId
            ORDER BY MAX(m.createdAt) DESC
            """)
    List<ChatSessionOverviewProjection> findSessionOverviewsByUserId(@Param("userId") UUID userId);

    Optional<ChatMessageEntity> findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(
        UUID sessionId, UUID userId, String role);

    @Modifying
    @Query(value = "DELETE FROM chat_messages WHERE session_id = :sessionId AND user_id = :userId", nativeQuery = true)
    void deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);
}
```

(`deleteBySessionIdAndUserId` stays native — it only touches `session_id`/`user_id`, neither of which is encrypted, so there's no bypass concern.)

- [ ] **Step 5: Rework `ChatHistoryService.listSessions`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java`:

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
    private static final int MAX_SESSIONS = 50;

    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSessionSummaryDto> listSessions(UUID userId) {
        return chatMessageRepository.findSessionOverviewsByUserId(userId).stream()
            .limit(MAX_SESSIONS)
            .map(o -> new ChatSessionSummaryDto(
                o.getSessionId(),
                truncateTitle(titleSourceFor(userId, o.getSessionId())),
                o.getLastMessageAt()))
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

    private String titleSourceFor(UUID userId, UUID sessionId) {
        return chatMessageRepository
            .findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER")
            .map(ChatMessageEntity::getContent)
            .orElse(null);
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

- [ ] **Step 6: Rewrite `ChatHistoryServiceTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock ChatMessageRepository chatMessageRepository;
    @InjectMocks ChatHistoryService service;

    private ChatSessionOverviewProjection overview(UUID sessionId, Instant lastMessageAt) {
        return new ChatSessionOverviewProjection() {
            @Override public UUID getSessionId() { return sessionId; }
            @Override public Instant getLastMessageAt() { return lastMessageAt; }
        };
    }

    private ChatMessageEntity userMessage(UUID sessionId, UUID userId, String content) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setSessionId(sessionId);
        e.setUserId(userId);
        e.setRole("USER");
        e.setContent(content);
        return e;
    }

    @Test
    void listSessions_mapsOverviewsToDtosWithTruncatedTitle() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        String longMessage = "x".repeat(80);
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, lastMessageAt)));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.of(userMessage(sessionId, userId, longMessage)));

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
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, Instant.now())));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.of(userMessage(sessionId, userId, "Short message")));

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEqualTo("Short message");
    }

    @Test
    void listSessions_noUserMessageYet_titleIsEmptyString() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(chatMessageRepository.findSessionOverviewsByUserId(userId))
            .thenReturn(List.of(overview(sessionId, Instant.now())));
        when(chatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER"))
            .thenReturn(Optional.empty());

        List<ChatSessionSummaryDto> result = service.listSessions(userId);

        assertThat(result.get(0).title()).isEmpty();
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

- [ ] **Step 7: Rewrite `ChatMessageRepositoryTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java`:

```java
package com.kropholler.dev.hermes.ai.chat;

import jakarta.persistence.EntityManager;
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

import java.util.List;
import java.util.Optional;
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
    @Autowired EntityManager em;

    private ChatMessageEntity saveMessage(UUID sessionId, UUID userId, String role, String content) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setSessionId(sessionId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        return chatMessageRepository.saveAndFlush(m);
    }

    @Test
    void content_isStoredEncryptedAtRest() {
        UUID userId = UUID.randomUUID();
        ChatMessageEntity saved = saveMessage(UUID.randomUUID(), userId, "USER", "a very secret message");
        em.clear();

        Object rawContent = em.createNativeQuery("SELECT content FROM chat_messages WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();

        assertThat(rawContent).isNotEqualTo("a very secret message");
        assertThat(chatMessageRepository.findById(saved.getId()).orElseThrow().getContent())
            .isEqualTo("a very secret message");
    }

    @Test
    void findSessionOverviewsByUserId_groupsBySessionOrderedByMostRecentFirst() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID olderSession = UUID.randomUUID();
        UUID newerSession = UUID.randomUUID();
        saveMessage(olderSession, userId, "USER", "Older conversation");
        Thread.sleep(10); // ensure a strictly later created_at for the newer session
        saveMessage(newerSession, userId, "USER", "Newer conversation");

        List<ChatSessionOverviewProjection> result = chatMessageRepository.findSessionOverviewsByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSessionId()).isEqualTo(newerSession);
        assertThat(result.get(1).getSessionId()).isEqualTo(olderSession);
    }

    @Test
    void findSessionOverviewsByUserId_onlyReturnsCallersOwnSessions() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        saveMessage(UUID.randomUUID(), otherUserId, "USER", "Someone else's conversation");

        List<ChatSessionOverviewProjection> result = chatMessageRepository.findSessionOverviewsByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc_returnsFirstUserMessageDecrypted() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, userId, "USER", "First message in this conversation");
        saveMessage(sessionId, userId, "ASSISTANT", "A reply");
        saveMessage(sessionId, userId, "USER", "A follow-up question");

        Optional<ChatMessageEntity> result = chatMessageRepository
            .findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER");

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("First message in this conversation");
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
        assertThat(chatMessageRepository.findSessionOverviewsByUserId(otherUserId)).hasSize(1);
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

- [ ] **Step 8: Run the tests**

Run: `cd hermes-backend && mvn -q test -Dtest=ChatHistoryServiceTest,ChatMessageRepositoryTest`
Expected: PASS. `ChatMessageRepositoryTest` requires Docker (Testcontainers) to be running.

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionOverviewProjection.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java
git commit -m "feat: encrypt chat message content and rework session-title query"
```

---

### Task 4: Encrypt notification title and body

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationRepositoryTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2.

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.notification;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired NotificationRepository repository;
    @Autowired EntityManager em;

    @Test
    void titleAndBody_areStoredEncryptedAndDecryptTransparently() {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(UUID.randomUUID());
        entity.setTitle("Price alert");
        entity.setBody("Your favorite listing dropped 10%");

        NotificationEntity saved = repository.saveAndFlush(entity);
        em.clear();

        Object rawTitle = em.createNativeQuery("SELECT title FROM notifications WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        assertThat(rawTitle).isNotEqualTo("Price alert");

        NotificationEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Price alert");
        assertThat(reloaded.getBody()).isEqualTo("Your favorite listing dropped 10%");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hermes-backend && mvn -q test -Dtest=NotificationRepositoryTest`
Expected: FAIL — `rawTitle` equals `"Price alert"` (not yet encrypted).

- [ ] **Step 3: Apply the converter**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java`, add the import and annotations:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
class NotificationEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID taskId;

    @Column(nullable = false)
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String listingIds = "[]";

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant emailSentAt;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q test -Dtest=NotificationRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationRepositoryTest.java
git commit -m "feat: encrypt notification title and body"
```

---

### Task 5: Encrypt user profile fields + rework email sync to plain JPA

Applies encryption to every `UserProfileEntity` field and, per spec Section 3 fix #2, replaces the native `upsertEmail` with a JPQL update + JPA-insert fallback so no native SQL bypasses the converters.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`
- Delete: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpdateEmailTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter`, `EncryptedDoubleConverter` from Task 2.
- Produces: `UserProfileRepository.updateEmail(UUID userId, String email): int` (replaces `upsertEmail`, which no longer exists). `UserProfileService.syncEmail(UUID, String)` keeps its existing signature and null/blank no-op behavior.

- [ ] **Step 1: Apply converters to `UserProfileEntity`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfileEntity {

    @Id
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    private String street;
    @Convert(converter = EncryptedStringConverter.class)
    private String houseNumber;
    @Convert(converter = EncryptedStringConverter.class)
    private String houseNumberAddition;
    @Convert(converter = EncryptedStringConverter.class)
    private String zipCode;
    @Convert(converter = EncryptedStringConverter.class)
    private String city;
    @Convert(converter = EncryptedStringConverter.class)
    private String province;
    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    @Convert(converter = EncryptedDoubleConverter.class)
    private Double latitude;
    @Convert(converter = EncryptedDoubleConverter.class)
    private Double longitude;

    private Instant updatedAt;
}
```

- [ ] **Step 2: Replace `upsertEmail` with a JPQL `updateEmail`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`:

```java
package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying
    @Query("UPDATE UserProfileEntity u SET u.email = :email WHERE u.userId = :userId")
    int updateEmail(@Param("userId") UUID userId, @Param("email") String email);
}
```

- [ ] **Step 3: Rework `UserProfileService.syncEmail` with the insert fallback + race guard**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`, add the `DataIntegrityViolationException` import and replace the `syncEmail` method:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository repository;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public AddressDto getProfile(UUID userId) {
        return repository.findById(userId)
            .map(UserProfileService::toDto)
            .orElseGet(AddressDto::empty);
    }

    @Transactional
    public AddressDto updateAddress(UUID userId, String street, String houseNumber,
            String houseNumberAddition, String zipCode, String city, String province) {
        GeocodeResult geocodeResult = geocodingService.geocodeAddress(houseNumber, street, city)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Address could not be geocoded"));

        UserProfileEntity entity = repository.findById(userId).orElseGet(() -> {
            UserProfileEntity e = new UserProfileEntity();
            e.setUserId(userId);
            return e;
        });
        entity.setStreet(street);
        entity.setHouseNumber(houseNumber);
        entity.setHouseNumberAddition(houseNumberAddition);
        entity.setZipCode(zipCode);
        entity.setCity(city);
        entity.setProvince(province);
        entity.setLatitude(geocodeResult.lat());
        entity.setLongitude(geocodeResult.lon());
        entity.setUpdatedAt(Instant.now());

        return toDto(repository.save(entity));
    }

    @Transactional
    public void syncEmail(UUID userId, String email) {
        if (email == null || email.isBlank()) return;
        int updated = repository.updateEmail(userId, email);
        if (updated == 0) {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(userId);
            entity.setEmail(email);
            entity.setUpdatedAt(Instant.now());
            try {
                repository.save(entity);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with a concurrent syncEmail call for the same new user — the row
                // now exists, so retry as an update instead of failing the request.
                repository.updateEmail(userId, email);
            }
        }
    }

    private static AddressDto toDto(UserProfileEntity entity) {
        return new AddressDto(
            entity.getStreet(),
            entity.getHouseNumber(),
            entity.getHouseNumberAddition(),
            entity.getZipCode(),
            entity.getCity(),
            entity.getProvince(),
            entity.getLatitude(),
            entity.getLongitude()
        );
    }
}
```

- [ ] **Step 4: Update `UserProfileServiceTest`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`, add the import `org.springframework.dao.DataIntegrityViolationException;` and replace the three `syncEmail_*` tests at the bottom of the class with:

```java
    @Test
    void syncEmail_existingRow_updatesViaJpql() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(1);

        service.syncEmail(userId, "user@hermes.local");

        verify(repository).updateEmail(userId, "user@hermes.local");
        verify(repository, never()).save(any());
    }

    @Test
    void syncEmail_noExistingRow_insertsNewProfile() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(0);

        service.syncEmail(userId, "user@hermes.local");

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@hermes.local");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void syncEmail_raceOnInsert_retriesAsUpdate() {
        UUID userId = UUID.randomUUID();
        when(repository.updateEmail(userId, "user@hermes.local")).thenReturn(0);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        service.syncEmail(userId, "user@hermes.local");

        verify(repository, times(2)).updateEmail(userId, "user@hermes.local");
    }

    @Test
    void syncEmail_nullEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), null);

        verifyNoInteractions(repository);
    }

    @Test
    void syncEmail_blankEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), "   ");

        verifyNoInteractions(repository);
    }
```

(This removes the old `syncEmail_delegatesToRepositoryUpsert` test, which referenced the now-deleted `upsertEmail` method.)

- [ ] **Step 5: Replace the repository-level upsert test**

```bash
git rm hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java
```

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpdateEmailTest.java`:

```java
package com.kropholler.dev.hermes.profile;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserProfileRepositoryUpdateEmailTest {

    @Autowired UserProfileRepository repository;
    @Autowired EntityManager em;

    @Test
    void updateEmail_existingRow_updatesEmailOnlyAndLeavesUpdatedAtUntouched() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Dorpstraat");
        existing.setEmail("old@hermes.local");
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        existing.setUpdatedAt(originalUpdatedAt);
        repository.saveAndFlush(existing);
        em.clear();

        int updated = repository.updateEmail(userId, "new@hermes.local");
        em.clear();

        assertThat(updated).isEqualTo(1);
        UserProfileEntity reloaded = repository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@hermes.local");
        assertThat(reloaded.getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateEmail_noExistingRow_returnsZeroAndCreatesNoRow() {
        UUID userId = UUID.randomUUID();

        int updated = repository.updateEmail(userId, "user@hermes.local");

        assertThat(updated).isEqualTo(0);
        assertThat(repository.findById(userId)).isEmpty();
    }

    @Test
    void email_isStoredEncryptedAtRest() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setEmail("user@hermes.local");
        entity.setUpdatedAt(Instant.now());
        UserProfileEntity saved = repository.saveAndFlush(entity);
        em.clear();

        Object rawEmail = em.createNativeQuery("SELECT email FROM user_profiles WHERE user_id = :id")
            .setParameter("id", saved.getUserId())
            .getSingleResult();

        assertThat(rawEmail).isNotEqualTo("user@hermes.local");
        assertThat(repository.findById(userId).orElseThrow().getEmail()).isEqualTo("user@hermes.local");
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd hermes-backend && mvn -q test -Dtest=UserProfileServiceTest,UserProfileRepositoryUpdateEmailTest,UserProfileRepositoryTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpdateEmailTest.java
git commit -m "feat: encrypt user profile fields, replace native upsertEmail with JPQL update+insert"
```

---

### Task 6: Encrypt agent task name and payload

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepositoryTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2.

- [ ] **Step 1: Write the failing test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepositoryTest.java`, add the `EntityManager` field and import, and a new test method:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AgentTaskRepositoryTest {

    @Autowired
    AgentTaskRepository repo;

    @Autowired
    EntityManager em;

    @Test
    void findsDueActiveTasks() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(UUID.randomUUID());
        task.setName("test watch");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().minusSeconds(60));
        repo.save(task);

        List<AgentTaskEntity> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getName()).isEqualTo("test watch");
    }

    @Test
    void doesNotReturnFutureTasks() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.RESEARCH);
        task.setUserId(UUID.randomUUID());
        task.setName("future task");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().plusSeconds(3600));
        repo.save(task);

        List<AgentTaskEntity> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).isEmpty();
    }

    @Test
    void nameAndPayload_areStoredEncryptedAndDecryptTransparently() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.WATCH);
        task.setUserId(UUID.randomUUID());
        task.setName("Utrecht 3-bed watch");
        task.setPayload("{\"city\":\"Utrecht\"}");
        task.setNextRunAt(Instant.now());
        AgentTaskEntity saved = repo.saveAndFlush(task);
        em.clear();

        Object rawName = em.createNativeQuery("SELECT name FROM agent_tasks WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
        assertThat(rawName).isNotEqualTo("Utrecht 3-bed watch");

        AgentTaskEntity reloaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Utrecht 3-bed watch");
        assertThat(reloaded.getPayload()).isEqualTo("{\"city\":\"Utrecht\"}");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hermes-backend && mvn -q test -Dtest=AgentTaskRepositoryTest`
Expected: FAIL — `rawName` equals `"Utrecht 3-bed watch"` (not yet encrypted).

- [ ] **Step 3: Apply the converter and drop the JSON typing on `payload`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
@Getter @Setter @NoArgsConstructor
public class AgentTaskEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskStatus status = AgentTaskStatus.ACTIVE;

    @Column(nullable = false)
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload = "{}";

    private String schedule;
    private Instant lastRunAt;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q test -Dtest=AgentTaskRepositoryTest`
Expected: PASS (3/3 tests green)

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepositoryTest.java
git commit -m "feat: encrypt agent task name and payload"
```

---

### Task 7: Flyway migration to widen/retype the affected columns

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V15__encrypt_sensitive_columns.sql`

**Interfaces:**
- Consumes: nothing new — this is a pure schema migration matching the entity changes from Tasks 3–6.

- [ ] **Step 1: Write the migration**

```sql
TRUNCATE TABLE chat_messages;
TRUNCATE TABLE agent_tasks, notifications;
TRUNCATE TABLE user_profiles;

ALTER TABLE notifications ALTER COLUMN title TYPE TEXT;

ALTER TABLE user_profiles ALTER COLUMN street TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN house_number TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN house_number_addition TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN zip_code TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN city TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN province TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN email TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN latitude TYPE TEXT USING latitude::text;
ALTER TABLE user_profiles ALTER COLUMN longitude TYPE TEXT USING longitude::text;

ALTER TABLE agent_tasks ALTER COLUMN name TYPE TEXT;
ALTER TABLE agent_tasks ALTER COLUMN payload DROP DEFAULT;
ALTER TABLE agent_tasks ALTER COLUMN payload TYPE TEXT USING payload::text;
ALTER TABLE agent_tasks ALTER COLUMN payload SET DEFAULT '{}';
```

(`chat_messages.content` and `notifications.body` are already `TEXT` — no change needed. Truncating `notifications`/`agent_tasks` in one `TRUNCATE` statement avoids needing `CASCADE` for the `notifications.task_id → agent_tasks.id` foreign key, since Postgres resolves ordering across tables listed in the same statement.)

- [ ] **Step 2: Run the full backend test suite**

Run: `cd hermes-backend && mvn test`
Expected: PASS, all tests green. This is the real verification of the migration SQL — `ChatMessageRepositoryTest`, `ListingRepositoryGeoTest`, and `ListingRepositoryRadiusTest` all boot a Postgres+PostGIS Testcontainers instance with `spring.flyway.enabled=true`, which runs the full migration chain including `V15` before Hibernate's `ddl-auto=validate` check. A syntax error or type mismatch in `V15` will surface as a Flyway migration failure in any of those test classes.

If any test class fails to start with a Flyway error, read the migration failure message (it names the exact statement and reason), fix the SQL in `V15__encrypt_sensitive_columns.sql`, and re-run.

- [ ] **Step 3: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V15__encrypt_sensitive_columns.sql
git commit -m "feat: add V15 migration widening encrypted columns to TEXT"
```

---

## Self-Review Notes

- **Spec coverage:** Section 1 (architecture) → Tasks 1–2. Section 2 (field table) → Tasks 3–6, one task per entity group. Section 3 fix #1 (chat title query) → Task 3. Section 3 fix #2 (`upsertEmail` → JPQL) → Task 5. Section 4 (migration + testing) → Task 7 plus the `UserProfileRepositoryUpdateEmailTest`/`NotificationRepositoryTest` additions in Tasks 4–5, which drop the Testcontainers dependency the old upsert test needed.
- **Type consistency checked:** `updateEmail(UUID, String): int` name/signature matches between `UserProfileRepository` (Task 5, Step 2) and every caller/test (`UserProfileService`, `UserProfileServiceTest`, `UserProfileRepositoryUpdateEmailTest`). `ChatSessionOverviewProjection`/`findSessionOverviewsByUserId`/`findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc` names match across `ChatMessageRepository`, `ChatHistoryService`, and both chat test files.
- **No placeholders:** every step above shows complete, compilable code and exact commands.

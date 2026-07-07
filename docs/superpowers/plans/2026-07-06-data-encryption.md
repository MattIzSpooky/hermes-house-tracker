# Data Encryption for Sensitive Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Encrypt sensitive columns (chat message content, notification title/body, user profile address/email/geocode, agent task name/payload) at rest, transparently, without touching any caller code outside the entities/repositories/services listed below.

**Architecture:** A new `crypto` package provides a `FieldEncryptor` component wrapping one `org.springframework.security.crypto.encrypt.Encryptors.text(key, salt)` instance per configured key version (backed by an `EncryptionProperties` `@ConfigurationProperties` record), plus two Spring-managed JPA `AttributeConverter`s (`EncryptedStringConverter`, `EncryptedDoubleConverter`) that delegate to it. Entities apply `@Convert` per field. `FieldEncryptor.encrypt` prefixes ciphertext with the current key version (e.g. `2:<hex>`); `decrypt` parses that prefix to pick the right key, so multiple key versions can coexist across rotations. Each encrypted entity also carries a row-level `encryptionKeyVersion` column, stamped by a shared `EncryptionKeyVersionListener` JPA entity listener — this is *only* for cheap SQL filtering during rotation, not for decrypt correctness. Two existing bypasses of JPA hydration are fixed as part of this work: the chat-session-title native query (reworked into entity-hydrating queries) and the `upsertEmail` native blind-upsert (replaced by a JPQL update + JPA insert fallback). Migrations `V15` and `V16` add the `encryption_key_version` column and truncate/widen/retype the affected columns to `TEXT` — split across two files because `ChatMessageRepositoryTest` is the only repository test validating against real Flyway-migrated Postgres, so `chat_messages.encryption_key_version` (`V15`) has to exist before Task 8's broader migration (`V16`) would otherwise land. A last task adds an admin-triggered `ReencryptionRunner` batch job that migrates rows still on an old key version onto the current one after a rotation.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA / Hibernate 6, `spring-security-crypto` (already on the classpath transitively via `spring-boot-starter-oauth2-resource-server` — no new dependency), Flyway, PostgreSQL (+PostGIS) via Testcontainers for integration tests, H2 for plain `@DataJpaTest`s.

## Global Constraints

- Fields to encrypt (from spec Section 2): `ChatMessageEntity.content`; `NotificationEntity.title`, `body`; `UserProfileEntity.street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `email`, `latitude`, `longitude`; `AgentTaskEntity.name`, `payload`.
- Explicitly NOT encrypted: `NotificationEntity.listingIds`, `AgentTaskEntity.type/status/schedule`, all `listings.*` columns.
- `hermes.encryption.keys.<n>` / `hermes.encryption.salts.<n>` (one pair per key version) plus `hermes.encryption.current-version` are required properties, env-var-backed in `application.properties`, fixed literal values (two versions) in test `application.properties` — see spec Section 3.
- `FieldEncryptor.encrypt` always embeds the current key version as a prefix in its output (`"<version>:<hex>"`); `decrypt` parses that prefix to select the matching key, so any number of key versions can coexist across a rotation without breaking old data.
- Every encrypted entity carries a row-level `encryptionKeyVersion` field (`encryption_key_version` column), stamped via the shared `EncryptionKeyVersionListener` (Task 3) — used only for cheap SQL filtering during rotation, never for decrypt correctness.
- `upsertEmail` (native `INSERT ... ON CONFLICT`) is replaced entirely by a JPQL `updateEmail` + JPA-insert-fallback pair (never re-add native SQL for this path) — see spec Section 4.
- The insert-fallback path in `UserProfileService.syncEmail` must catch `DataIntegrityViolationException` and retry as an update (race guard — spec Section 4).
- `V15__add_chat_messages_encryption_key_version.sql` (Task 4) adds only `chat_messages.encryption_key_version`, needed early because `ChatMessageRepositoryTest` validates against real Flyway-migrated Postgres. `V16__encrypt_sensitive_columns.sql` (Task 8) truncates `chat_messages`, `notifications`, `user_profiles`, `agent_tasks`, alters the remaining column types, and adds `encryption_key_version` to the other three tables — existing dev data in those tables is not preserved.
- The re-encryption job (Task 9) never relies on JPA dirty-checking to force a rewrite — plaintext values re-set on an already-loaded entity are equal to their loaded snapshot, so Hibernate would treat the entity as unchanged and silently skip the `UPDATE`. It instead issues an explicit JPQL bulk update per row, passing back the decrypted plaintext so the converter re-encrypts it under the current version during translation.
- No change to the PostGIS radius-search code path or the `listings` table.

---

### Task 1: `FieldEncryptor` + multi-version encryption properties

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionProperties.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/FieldEncryptor.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/FieldEncryptorTest.java`
- Modify: `hermes-backend/src/main/resources/application.properties`
- Modify: `hermes-backend/src/test/resources/application.properties`

**Interfaces:**
- Produces: `EncryptionProperties(Map<Integer, String> keys, Map<Integer, String> salts, int currentVersion)` — a `@ConfigurationProperties(prefix = "hermes.encryption")` record. `FieldEncryptor(EncryptionProperties properties)` constructor; `int getCurrentVersion()`; `String encrypt(String plaintext)`; `String decrypt(String stored)` — both null-safe (null in → null out). `encrypt` prefixes its output with the current version (`"<version>:<hex>"`); `decrypt` parses that prefix to pick the matching key. Used by every converter in Task 2, `EncryptionKeyVersionListener` in Task 3, and `ReencryptionRunner` in Task 9.

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private final EncryptionProperties properties = new EncryptionProperties(
        Map.of(1, "old-key", 2, "new-key"),
        Map.of(1, "abcd1234abcd1234", 2, "1234abcd1234abcd"),
        2
    );
    private final FieldEncryptor encryptor = new FieldEncryptor(properties);

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).isNotEqualTo("hello@hermes.local");
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void encrypt_prefixesCiphertextWithCurrentVersion() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).startsWith("2:");
    }

    @Test
    void decrypt_supportsValueEncryptedUnderAnOlderVersion() {
        FieldEncryptor olderEncryptor = new FieldEncryptor(new EncryptionProperties(
            Map.of(1, "old-key"), Map.of(1, "abcd1234abcd1234"), 1));
        String oldCiphertext = olderEncryptor.encrypt("hello@hermes.local");

        assertThat(oldCiphertext).startsWith("1:");
        assertThat(encryptor.decrypt(oldCiphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void decrypt_unknownVersion_throws() {
        assertThatThrownBy(() -> encryptor.decrypt("99:deadbeef"))
            .isInstanceOf(IllegalStateException.class);
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
Expected: FAIL to compile — `FieldEncryptor`/`EncryptionProperties` do not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.kropholler.dev.hermes.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "hermes.encryption")
public record EncryptionProperties(Map<Integer, String> keys, Map<Integer, String> salts, int currentVersion) {
}
```

```java
package com.kropholler.dev.hermes.crypto;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FieldEncryptor {

    private static final String VERSION_SEPARATOR = ":";

    private final Map<Integer, TextEncryptor> encryptorsByVersion;
    private final int currentVersion;

    public FieldEncryptor(EncryptionProperties properties) {
        this.currentVersion = properties.currentVersion();
        this.encryptorsByVersion = properties.keys().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> Encryptors.text(e.getValue(), properties.salts().get(e.getKey()))));
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        String ciphertext = requireEncryptor(currentVersion).encrypt(plaintext);
        return currentVersion + VERSION_SEPARATOR + ciphertext;
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        int separatorIndex = stored.indexOf(VERSION_SEPARATOR);
        int version = Integer.parseInt(stored.substring(0, separatorIndex));
        String ciphertext = stored.substring(separatorIndex + 1);
        return requireEncryptor(version).decrypt(ciphertext);
    }

    private TextEncryptor requireEncryptor(int version) {
        TextEncryptor encryptor = encryptorsByVersion.get(version);
        if (encryptor == null) {
            throw new IllegalStateException("No encryption key configured for version " + version);
        }
        return encryptor;
    }
}
```

- [ ] **Step 4: Add the properties**

In `hermes-backend/src/main/resources/application.properties`, append:

```properties

# Field-level encryption (see docs/superpowers/specs/2026-07-06-data-encryption-design.md)
# Add a new hermes.encryption.keys.<n>/salts.<n> pair and bump current-version to rotate;
# keep old versions configured until ReencryptionRunner confirms no row still references them.
hermes.encryption.keys.1=${HERMES_ENCRYPTION_KEY_V1}
hermes.encryption.salts.1=${HERMES_ENCRYPTION_SALT_V1}
hermes.encryption.current-version=1
```

In `hermes-backend/src/test/resources/application.properties`, append:

```properties
hermes.encryption.keys.1=test-encryption-key-v1
hermes.encryption.salts.1=abcd1234abcd1234
hermes.encryption.keys.2=test-encryption-key-v2
hermes.encryption.salts.2=1234abcd1234abcd
hermes.encryption.current-version=1
```

(Two versions are configured in tests, with `current-version=1`, so `ReencryptionRunnerTest`/`*ReencryptTest` in Task 9 can bump the *effective* current version per-test via `@DynamicPropertySource`/`@TestPropertySource` overrides and exercise real rotation behavior. Salts must be valid hex strings per `Encryptors.text`'s contract — both sample values above qualify.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=FieldEncryptorTest`
Expected: PASS (6/6 tests green)

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionProperties.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/FieldEncryptor.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/FieldEncryptorTest.java \
        hermes-backend/src/main/resources/application.properties \
        hermes-backend/src/test/resources/application.properties
git commit -m "feat: add multi-version FieldEncryptor with embedded key-version prefix"
```

---

### Task 2: `EncryptedStringConverter` and `EncryptedDoubleConverter`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverter.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverter.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedStringConverterTest.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptedDoubleConverterTest.java`

**Interfaces:**
- Consumes: `FieldEncryptor` from Task 1 (constructor-injected). The converters are unaware of the embedded version prefix — that's entirely `FieldEncryptor`'s concern.
- Produces: `EncryptedStringConverter implements AttributeConverter<String, String>`; `EncryptedDoubleConverter implements AttributeConverter<Double, String>`. Both are `@Component @Converter(autoApply = false)` — referenced explicitly via `@Convert(converter = ...)` on entity fields in Tasks 4–7.

- [ ] **Step 1: Write the failing tests**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(new EncryptionProperties(
        Map.of(1, "test-encryption-key-v1"), Map.of(1, "abcd1234abcd1234"), 1));
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedDoubleConverterTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(new EncryptionProperties(
        Map.of(1, "test-encryption-key-v1"), Map.of(1, "abcd1234abcd1234"), 1));
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

### Task 3: `EncryptionVersioned` + `EncryptionKeyVersionListener`

Adds the row-level key-version stamping mechanism used by every encrypted entity (Tasks 4–7) and consulted by the re-encryption job (Task 9). `AttributeConverter`s only see one column, so they can't stamp a sibling `encryptionKeyVersion` field themselves — a JPA entity listener, which sees the whole entity, does that instead. This column is purely for cheap SQL filtering during rotation; decrypt correctness comes entirely from the version prefix embedded in each field's ciphertext (Task 1), not from this column.

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionVersioned.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionKeyVersionListener.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptionKeyVersionListenerTest.java`

**Interfaces:**
- Consumes: `FieldEncryptor.getCurrentVersion()` from Task 1.
- Produces: `EncryptionVersioned` (a one-method interface every encrypted entity implements: `void setEncryptionKeyVersion(Integer version)` — satisfied automatically by Lombok's `@Setter` once the field is added in Tasks 4–7). `EncryptionKeyVersionListener`, referenced via `@EntityListeners(EncryptionKeyVersionListener.class)` on each encrypted entity.

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptionKeyVersionListenerTest {

    @Mock FieldEncryptor fieldEncryptor;
    @InjectMocks EncryptionKeyVersionListener listener;

    static class Versioned implements EncryptionVersioned {
        private Integer version;

        @Override
        public void setEncryptionKeyVersion(Integer version) {
            this.version = version;
        }

        Integer getVersion() {
            return version;
        }
    }

    @Test
    void stampCurrentVersion_setsCurrentVersionOnVersionedEntity() {
        when(fieldEncryptor.getCurrentVersion()).thenReturn(2);
        Versioned entity = new Versioned();

        listener.stampCurrentVersion(entity);

        assertThat(entity.getVersion()).isEqualTo(2);
    }

    @Test
    void stampCurrentVersion_ignoresNonVersionedEntity() {
        listener.stampCurrentVersion(new Object());
        // No exception, and no interaction with fieldEncryptor needed to reach this point.
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=EncryptionKeyVersionListenerTest`
Expected: FAIL to compile — `EncryptionVersioned`/`EncryptionKeyVersionListener` do not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.kropholler.dev.hermes.crypto;

public interface EncryptionVersioned {
    void setEncryptionKeyVersion(Integer version);
}
```

```java
package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyVersionListener {

    private final FieldEncryptor fieldEncryptor;

    public EncryptionKeyVersionListener(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @PrePersist
    @PreUpdate
    public void stampCurrentVersion(Object entity) {
        if (entity instanceof EncryptionVersioned versioned) {
            versioned.setEncryptionKeyVersion(fieldEncryptor.getCurrentVersion());
        }
    }
}
```

(Same Hibernate `SpringBeanContainer` mechanism already used for the `@Convert`-referenced converters in Task 2 lets `@EntityListeners(EncryptionKeyVersionListener.class)` resolve this as a Spring-managed, constructor-injected bean rather than instantiating it with a no-arg constructor.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=EncryptionKeyVersionListenerTest`
Expected: PASS (2/2 tests green)

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionVersioned.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/EncryptionKeyVersionListener.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/EncryptionKeyVersionListenerTest.java
git commit -m "feat: add EncryptionVersioned and EncryptionKeyVersionListener"
```

---

### Task 4: Encrypt chat message content + rework the session-title query

This task both applies encryption to `ChatMessageEntity.content` and fixes the one native-SQL bypass this creates (spec Section 4, fix #1) — they must ship together or the chat sidebar breaks.

**Migration-ordering note:** `ChatMessageRepositoryTest` is the only repository test in this whole plan that boots real Postgres via Testcontainers with `spring.flyway.enabled=true` and `ddl-auto=validate` — every other encrypted-entity test (Tasks 5–7) runs against H2 with Hibernate auto-generating the schema, so it never needs a real migration to be in place first. That means this task's entity change (adding `encryptionKeyVersion`) needs the `chat_messages.encryption_key_version` column to actually exist in the Flyway-migrated test database *now*, not after Task 8's migration lands. This task therefore gets its own small, additive migration (`V15`); Task 8's migration is renumbered to `V16` and no longer adds this column to `chat_messages` (it still does so for the other three tables).

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionOverviewProjection.java`
- Delete: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionProjection.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java`
- Create: `hermes-backend/src/main/resources/db/migration/V15__add_chat_messages_encryption_key_version.sql`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2; `EncryptionVersioned`, `EncryptionKeyVersionListener` from Task 3.
- Produces: `ChatMessageRepository.findSessionOverviewsByUserId(UUID userId): List<ChatSessionOverviewProjection>`; `ChatMessageRepository.findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(UUID sessionId, UUID userId, String role): Optional<ChatMessageEntity>`; `ChatMessageEntity.getEncryptionKeyVersion()/setEncryptionKeyVersion(Integer)` (consumed by `ReencryptionRunner` in Task 9). `ChatSessionProjection` and `ChatMessageRepository.findSessionSummariesByUserId` no longer exist — nothing outside this task references them.

- [ ] **Step 1: Apply the converter to `ChatMessageEntity.content`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java`, add the import and annotation:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity implements EncryptionVersioned {

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

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

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

`@DataJpaTest` does not component-scan plain `@Component` beans, so `FieldEncryptor`, `EncryptedStringConverter`, and `EncryptionKeyVersionListener` must be pulled in explicitly via `@Import`, with `EncryptionProperties` via `@EnableConfigurationProperties` — otherwise the converter on `content` fails to resolve with `NoSuchBeanDefinitionException: FieldEncryptor`:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({
    ChatMessageRepositoryTest.Containers.class,
    FieldEncryptor.class,
    EncryptedStringConverter.class,
    EncryptionKeyVersionListener.class
})
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
        assertThat(saved.getEncryptionKeyVersion()).isEqualTo(1);
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

- [ ] **Step 8: Write the additive migration for `chat_messages.encryption_key_version`**

`ChatMessageRepositoryTest` boots real Postgres via Testcontainers with Flyway migrations applied and `ddl-auto=validate` — it needs this column to physically exist, not just be declared on the entity. Create `hermes-backend/src/main/resources/db/migration/V15__add_chat_messages_encryption_key_version.sql`:

```sql
ALTER TABLE chat_messages ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
```

(No truncate needed — this is a purely additive column with a default, safe on existing rows. The `TRUNCATE`/`TEXT`-widening migration in Task 8 is renumbered to `V16` and no longer adds this column to `chat_messages`, since it's already added here.)

- [ ] **Step 9: Run the tests**

Run: `cd hermes-backend && mvn -q test -Dtest=ChatHistoryServiceTest,ChatMessageRepositoryTest`
Expected: PASS. `ChatMessageRepositoryTest` requires Docker (Testcontainers) to be running.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageEntity.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatSessionOverviewProjection.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepositoryTest.java \
        hermes-backend/src/main/resources/db/migration/V15__add_chat_messages_encryption_key_version.sql
git commit -m "feat: encrypt chat message content and rework session-title query"
```

---

### Task 5: Encrypt notification title and body

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationEntity.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationRepositoryTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2; `EncryptionVersioned`, `EncryptionKeyVersionListener` from Task 3.
- Produces: `NotificationEntity.getEncryptionKeyVersion()/setEncryptionKeyVersion(Integer)` (consumed by `ReencryptionRunner` in Task 9).

- [ ] **Step 1: Write the failing test**

`@DataJpaTest` does not component-scan plain `@Component` beans (confirmed empirically in Task 4), so `FieldEncryptor`, `EncryptedStringConverter`, and `EncryptionKeyVersionListener` must be pulled in explicitly via `@Import`, with `EncryptionProperties` via `@EnableConfigurationProperties` — otherwise the converter on `NotificationEntity.title`/`body` fails to resolve with `NoSuchBeanDefinitionException: FieldEncryptor`:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptionKeyVersionListener.class})
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
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(1);
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
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
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
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter @Setter @NoArgsConstructor
class NotificationEntity implements EncryptionVersioned {

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

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

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

### Task 6: Encrypt user profile fields + rework email sync to plain JPA

Applies encryption to every `UserProfileEntity` field and, per spec Section 4 fix #2, replaces the native `upsertEmail` with a JPQL update + JPA-insert fallback so no native SQL bypasses the converters.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`
- Delete: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpdateEmailTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter`, `EncryptedDoubleConverter` from Task 2; `EncryptionVersioned`, `EncryptionKeyVersionListener` from Task 3.
- Produces: `UserProfileRepository.updateEmail(UUID userId, String email): int` (replaces `upsertEmail`, which no longer exists). `UserProfileService.syncEmail(UUID, String)` keeps its existing signature and null/blank no-op behavior. `UserProfileEntity.getEncryptionKeyVersion()/setEncryptionKeyVersion(Integer)` (consumed by `ReencryptionRunner` in Task 9).

- [ ] **Step 1: Apply converters to `UserProfileEntity`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserProfileEntity implements EncryptionVersioned {

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

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

    private Instant updatedAt;
}
```

Note: `updateEmail`'s bulk `@Modifying @Query` UPDATE bypasses the `@PreUpdate` lifecycle callback entirely (bulk JPQL updates never invoke entity listeners), so it intentionally does **not** touch `encryption_key_version` — it leaves that column exactly as it was. This is safe: it can only make the row's version column *more conservative* (i.e. still flagged stale even though `email` itself is already re-encrypted under the current version via the converter), never falsely mark a row as fully migrated when some other field isn't. The re-encryption job (Task 9) may then redundantly re-encrypt an already-current `email` alongside genuinely stale fields on that row — wasted work, not a correctness bug.

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

`@DataJpaTest` does not component-scan plain `@Component` beans (confirmed empirically in Task 4), so `FieldEncryptor`, `EncryptedStringConverter`, `EncryptedDoubleConverter`, and `EncryptionKeyVersionListener` must be pulled in explicitly via `@Import`, with `EncryptionProperties` via `@EnableConfigurationProperties`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class})
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
        assertThat(saved.getEncryptionKeyVersion()).isEqualTo(1);
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

### Task 7: Encrypt agent task name and payload

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskEntity.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepositoryTest.java`

**Interfaces:**
- Consumes: `EncryptedStringConverter` from Task 2; `EncryptionVersioned`, `EncryptionKeyVersionListener` from Task 3.
- Produces: `AgentTaskEntity.getEncryptionKeyVersion()/setEncryptionKeyVersion(Integer)` (consumed by `ReencryptionRunner` in Task 9).

- [ ] **Step 1: Write the failing test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepositoryTest.java`, add the `EntityManager` field and import, and a new test method:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskRepository;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptionKeyVersionListener.class})
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
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(1);
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
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter @Setter @NoArgsConstructor
public class AgentTaskEntity implements EncryptionVersioned {

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

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

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

### Task 8: Flyway migration to widen/retype the affected columns and add remaining key-version columns

Task 4 already added `chat_messages.encryption_key_version` via its own `V15` migration (see the migration-ordering note in Task 4) — that was necessary because `ChatMessageRepositoryTest` is the only repository test in this plan that validates against real Flyway-migrated Postgres. This task's migration is `V16`; it does not touch `chat_messages.encryption_key_version` again.

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V16__encrypt_sensitive_columns.sql`

**Interfaces:**
- Consumes: nothing new — this is a pure schema migration matching the entity changes from Tasks 4–7.

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

ALTER TABLE notifications ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
ALTER TABLE user_profiles ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
ALTER TABLE agent_tasks ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
```

(`chat_messages.content` and `notifications.body` are already `TEXT` — no change needed, and `chat_messages.encryption_key_version` was already added by Task 4's `V15`. Truncating `notifications`/`agent_tasks` in one `TRUNCATE` statement avoids needing `CASCADE` for the `notifications.task_id → agent_tasks.id` foreign key, since Postgres resolves ordering across tables listed in the same statement. All tables here are truncated above, so `encryption_key_version DEFAULT 1` never needs to backfill any pre-existing row — every row inserted afterward goes through the `EncryptionKeyVersionListener`, which always stamps the real current version regardless of this column default.)

- [ ] **Step 2: Run the full backend test suite**

Run: `cd hermes-backend && mvn test`
Expected: PASS, all tests green. This is the real verification of the migration SQL — `ChatMessageRepositoryTest`, `ListingRepositoryGeoTest`, and `ListingRepositoryRadiusTest` all boot a Postgres+PostGIS Testcontainers instance with `spring.flyway.enabled=true`, which runs the full migration chain including `V15` and `V16` before Hibernate's `ddl-auto=validate` check. A syntax error or type mismatch in `V16` will surface as a Flyway migration failure in any of those test classes.

If any test class fails to start with a Flyway error, read the migration failure message (it names the exact statement and reason), fix the SQL in `V16__encrypt_sensitive_columns.sql`, and re-run.

- [ ] **Step 3: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V16__encrypt_sensitive_columns.sql
git commit -m "feat: add V16 migration widening encrypted columns to TEXT and adding remaining encryption_key_version columns"
```

---

### Task 9: Re-encryption tooling

Adds the admin-triggered batch job that migrates rows still on an old key version onto the current one after a rotation (spec Section 5).

**Design note on where the logic lives:** `NotificationRepository`/`NotificationEntity` and `AgentTaskRepository`/`AgentTaskEntity` are package-private (deliberately — they're internal to the `notification` and `ai.agent.task` packages). A single `ReencryptionRunner` in the `crypto` package can't reference them directly without widening that visibility just for this job. Instead, `crypto` defines a small `Reencryptable` interface; each owning package contributes its own package-private `@Component` implementing it (so it keeps full access to its own repository/entity); `ReencryptionRunner` only depends on the public `Reencryptable` interface and asks Spring to inject every bean that implements it — it never references a concrete entity or repository type.

**Design note on why this doesn't reuse "touch and save":** Hibernate's dirty-checking compares an entity's current attribute values to the snapshot it loaded — both are the same decrypted plaintext `String`, so re-setting a field to its own value looks unchanged and the `UPDATE` would silently never fire. Each `reencryptBatch()` implementation instead issues an explicit `@Modifying` JPQL bulk update, passing the *decrypted* plaintext back in as a bind parameter; Hibernate applies the `AttributeConverter` to that parameter during query translation, so it comes out re-encrypted under whatever `FieldEncryptor` currently considers the current version — no separate encrypt call needed in the job itself.

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/Reencryptable.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/ReencryptionRunner.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/ReencryptionRunnerTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageReencryptionTask.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageReencryptionTaskTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationReencryptionTask.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileReencryptionTask.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskReencryptionTask.java`

**Interfaces:**
- Consumes: `FieldEncryptor.getCurrentVersion()` from Task 1; `encryptionKeyVersion` field on all four entities from Tasks 4–7.
- Produces: `Reencryptable { String tableName(); int reencryptBatch(); }`. `ReencryptionRunner implements CommandLineRunner`, active only under the `reencrypt` Spring profile.

- [ ] **Step 1: Write the failing test for the central runner**

```java
package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReencryptionRunnerTest {

    @Mock Reencryptable chatMessages;
    @Mock Reencryptable notifications;

    @Test
    void run_loopsEachReencryptableUntilItReturnsZero() throws Exception {
        when(chatMessages.tableName()).thenReturn("chat_messages");
        when(chatMessages.reencryptBatch()).thenReturn(2, 1, 0);
        when(notifications.tableName()).thenReturn("notifications");
        when(notifications.reencryptBatch()).thenReturn(0);
        ReencryptionRunner runner = new ReencryptionRunner(List.of(chatMessages, notifications));

        runner.run();

        verify(chatMessages, org.mockito.Mockito.times(3)).reencryptBatch();
        verify(notifications, org.mockito.Mockito.times(1)).reencryptBatch();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=ReencryptionRunnerTest`
Expected: FAIL to compile — `Reencryptable`/`ReencryptionRunner` do not exist.

- [ ] **Step 3: Write `Reencryptable` and `ReencryptionRunner`**

```java
package com.kropholler.dev.hermes.crypto;

public interface Reencryptable {
    String tableName();
    int reencryptBatch();
}
```

```java
package com.kropholler.dev.hermes.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("reencrypt")
public class ReencryptionRunner implements CommandLineRunner {

    private final List<Reencryptable> reencryptables;

    public ReencryptionRunner(List<Reencryptable> reencryptables) {
        this.reencryptables = reencryptables;
    }

    @Override
    public void run(String... args) {
        for (Reencryptable reencryptable : reencryptables) {
            int processed;
            do {
                processed = reencryptable.reencryptBatch();
                log.info("Re-encrypted {} rows in {}", processed, reencryptable.tableName());
            } while (processed > 0);
        }
        log.info("Re-encryption complete for all tables.");
    }
}
```

(Not exposed as an HTTP endpoint — run manually via `java -jar hermes-backend.jar --spring.profiles.active=reencrypt` after bumping `hermes.encryption.current-version` and redeploying.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd hermes-backend && mvn -q -pl . -am test -Dtest=ReencryptionRunnerTest`
Expected: PASS

- [ ] **Step 5: Add paging/bulk-update repository methods and the per-entity `Reencryptable` tasks**

Add to `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java` (inside the existing interface, alongside the methods from Task 4):

```java
    List<ChatMessageEntity> findByEncryptionKeyVersionLessThan(int version, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessageEntity m SET m.content = :content, m.encryptionKeyVersion = :version WHERE m.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("content") String content, @Param("version") int version);
```

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class ChatMessageReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final FieldEncryptor fieldEncryptor;

    ChatMessageReencryptionTask(ChatMessageRepository chatMessageRepository, FieldEncryptor fieldEncryptor) {
        this.chatMessageRepository = chatMessageRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "chat_messages";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<ChatMessageEntity> stale = chatMessageRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (ChatMessageEntity m : stale) {
            chatMessageRepository.reencrypt(m.getId(), m.getContent(), version);
        }
        return stale.size();
    }
}
```

Add to `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationRepository.java` (full replacement, since Task 5 didn't already modify this file):

```java
package com.kropholler.dev.hermes.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadFalse(UUID userId);

    List<NotificationEntity> findByEncryptionKeyVersionLessThan(int version, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.title = :title, n.body = :body, n.encryptionKeyVersion = :version WHERE n.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("title") String title, @Param("body") String body, @Param("version") int version);
}
```

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class NotificationReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final FieldEncryptor fieldEncryptor;

    NotificationReencryptionTask(NotificationRepository notificationRepository, FieldEncryptor fieldEncryptor) {
        this.notificationRepository = notificationRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "notifications";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<NotificationEntity> stale = notificationRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (NotificationEntity n : stale) {
            notificationRepository.reencrypt(n.getId(), n.getTitle(), n.getBody(), version);
        }
        return stale.size();
    }
}
```

Add to `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java` (inside the existing interface, alongside `updateEmail` from Task 6):

```java
    List<UserProfileEntity> findByEncryptionKeyVersionLessThan(int version, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserProfileEntity u SET
                u.street = :street, u.houseNumber = :houseNumber, u.houseNumberAddition = :houseNumberAddition,
                u.zipCode = :zipCode, u.city = :city, u.province = :province, u.email = :email,
                u.latitude = :latitude, u.longitude = :longitude, u.encryptionKeyVersion = :version
            WHERE u.userId = :userId
            """)
    void reencrypt(@Param("userId") UUID userId, @Param("street") String street, @Param("houseNumber") String houseNumber,
            @Param("houseNumberAddition") String houseNumberAddition, @Param("zipCode") String zipCode,
            @Param("city") String city, @Param("province") String province, @Param("email") String email,
            @Param("latitude") Double latitude, @Param("longitude") Double longitude, @Param("version") int version);
```

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class UserProfileReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final UserProfileRepository userProfileRepository;
    private final FieldEncryptor fieldEncryptor;

    UserProfileReencryptionTask(UserProfileRepository userProfileRepository, FieldEncryptor fieldEncryptor) {
        this.userProfileRepository = userProfileRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "user_profiles";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<UserProfileEntity> stale = userProfileRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (UserProfileEntity p : stale) {
            userProfileRepository.reencrypt(p.getUserId(), p.getStreet(), p.getHouseNumber(),
                p.getHouseNumberAddition(), p.getZipCode(), p.getCity(), p.getProvince(), p.getEmail(),
                p.getLatitude(), p.getLongitude(), version);
        }
        return stale.size();
    }
}
```

Add to `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepository.java` (full replacement, since Task 7 didn't already modify this file):

```java
package com.kropholler.dev.hermes.ai.agent.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {
    List<AgentTaskEntity> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTaskEntity> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AgentTaskStatus status);

    List<AgentTaskEntity> findByEncryptionKeyVersionLessThan(int version, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgentTaskEntity t SET t.name = :name, t.payload = :payload, t.encryptionKeyVersion = :version WHERE t.id = :id")
    void reencrypt(@Param("id") UUID id, @Param("name") String name, @Param("payload") String payload, @Param("version") int version);
}
```

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import com.kropholler.dev.hermes.crypto.Reencryptable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class AgentTaskReencryptionTask implements Reencryptable {

    private static final int BATCH_SIZE = 500;

    private final AgentTaskRepository agentTaskRepository;
    private final FieldEncryptor fieldEncryptor;

    AgentTaskReencryptionTask(AgentTaskRepository agentTaskRepository, FieldEncryptor fieldEncryptor) {
        this.agentTaskRepository = agentTaskRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Override
    public String tableName() {
        return "agent_tasks";
    }

    @Override
    @Transactional
    public int reencryptBatch() {
        int version = fieldEncryptor.getCurrentVersion();
        List<AgentTaskEntity> stale = agentTaskRepository
            .findByEncryptionKeyVersionLessThan(version, PageRequest.of(0, BATCH_SIZE));
        for (AgentTaskEntity t : stale) {
            agentTaskRepository.reencrypt(t.getId(), t.getName(), t.getPayload(), version);
        }
        return stale.size();
    }
}
```

- [ ] **Step 6: Write the representative end-to-end integration test**

`ChatMessageReencryptionTaskTest` exercises the real converter + JPQL bulk-update path against H2. It simulates a row written before a rotation by inserting its ciphertext directly via native SQL (encrypted with the v1 key, tagged `encryption_key_version = 1`) rather than going through the entity listener — the listener always stamps whatever the *current* version is, so it can't produce "legacy" data on its own in a single test context. `NotificationReencryptionTaskTest`, `UserProfileReencryptionTaskTest`, and `AgentTaskReencryptionTaskTest` follow this exact same pattern for their own tables/fields and are not reproduced here.

`@DataJpaTest` does not component-scan plain `@Component` beans, so `FieldEncryptor`, `EncryptedStringConverter`, `EncryptionKeyVersionListener`, and `ChatMessageReencryptionTask` itself must be pulled in via `@Import`, with `EncryptionProperties` via `@EnableConfigurationProperties` (same wiring as Task 4's `ChatMessageRepositoryTest`):

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptionKeyVersionListener.class, ChatMessageReencryptionTask.class})
@TestPropertySource(properties = "hermes.encryption.current-version=2")
class ChatMessageReencryptionTaskTest {

    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired ChatMessageReencryptionTask task;
    @Autowired EntityManager em;

    @Test
    void reencryptBatch_migratesStaleRowsToCurrentVersionAndReturnsCountProcessed() {
        UUID id = UUID.randomUUID();
        String legacyCiphertext = "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234")
            .encrypt("a secret message");
        em.createNativeQuery("""
                INSERT INTO chat_messages (id, session_id, user_id, role, content, encryption_key_version, created_at)
                VALUES (:id, :sessionId, :userId, 'USER', :content, 1, now())
                """)
            .setParameter("id", id)
            .setParameter("sessionId", UUID.randomUUID())
            .setParameter("userId", UUID.randomUUID())
            .setParameter("content", legacyCiphertext)
            .executeUpdate();
        em.clear();

        int processed = task.reencryptBatch();
        em.clear();

        assertThat(processed).isEqualTo(1);
        ChatMessageEntity reloaded = chatMessageRepository.findById(id).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("a secret message");
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(2);

        Object rawContent = em.createNativeQuery("SELECT content FROM chat_messages WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        assertThat(rawContent.toString()).startsWith("2:");

        assertThat(task.reencryptBatch()).isEqualTo(0);
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `cd hermes-backend && mvn -q test -Dtest=ReencryptionRunnerTest,ChatMessageReencryptionTaskTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/Reencryptable.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/crypto/ReencryptionRunner.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/crypto/ReencryptionRunnerTest.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatMessageReencryptionTask.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatMessageReencryptionTaskTest.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationReencryptionTask.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileReencryptionTask.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskReencryptionTask.java
git commit -m "feat: add admin-triggered re-encryption tooling for key rotation"
```

---

## Self-Review Notes

- **Spec coverage:** Section 1 (architecture) → Tasks 1–2. Section 2 (field table) → Tasks 4–7, one task per entity group. Section 3 (key versioning and rotation) → Task 1 (embedded version prefix + multi-key config), Task 3 (row-level `encryption_key_version` column + listener), Tasks 4–7 (applying the column/interface/listener to each entity). Section 4 fix #1 (chat title query) → Task 4. Section 4 fix #2 (`upsertEmail` → JPQL) → Task 6. Section 5 (re-encryption tooling) → Task 9. Section 6 (migration + testing) → Task 8, plus the `UserProfileRepositoryUpdateEmailTest`/`NotificationRepositoryTest` additions in Tasks 5–6, which drop the Testcontainers dependency the old upsert test needed.
- **Type consistency checked:** `updateEmail(UUID, String): int` name/signature matches between `UserProfileRepository` (Task 6, Step 2) and every caller/test (`UserProfileService`, `UserProfileServiceTest`, `UserProfileRepositoryUpdateEmailTest`). `ChatSessionOverviewProjection`/`findSessionOverviewsByUserId`/`findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc` names match across `ChatMessageRepository`, `ChatHistoryService`, and both chat test files. `Reencryptable`'s two methods (`tableName()`, `reencryptBatch()`) and each `reencrypt(...)` bulk-update signature match between every repository (Task 9, Step 5) and its corresponding `*ReencryptionTask` implementation.
- **Package-visibility check:** `NotificationRepository`/`NotificationEntity` and `AgentTaskRepository`/`AgentTaskEntity` are package-private by design; Task 9 respects this by placing each entity's `Reencryptable` implementation inside its own package rather than centralizing entity-specific logic in `crypto`, so no existing visibility is widened.
- **Correctness check on Hibernate dirty-checking:** re-encryption cannot rely on loading an entity and re-saving it unchanged (Global Constraints) — Task 9's `reencryptBatch()` methods use explicit `@Modifying` JPQL bulk updates instead, so the `AttributeConverter` re-encrypts the passed-back plaintext during query translation regardless of entity dirty state.
- **No placeholders:** every step above shows complete, compilable code and exact commands, except the three non-representative `*ReencryptionTaskTest` files in Task 9, Step 6, which are explicitly named as following the one fully-written `ChatMessageReencryptionTaskTest` pattern rather than reproduced in full.

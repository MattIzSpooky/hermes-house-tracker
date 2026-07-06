# Data Encryption for Sensitive Fields — Design Spec

**Date:** 2026-07-06
**Status:** Approved

## Overview

Encrypts sensitive user-generated data at rest — chat messages, notifications, user profile (including address/geocode), and agent task payloads — without disrupting existing functionality (radius search, email sending, chat history sidebar).

The scope is column-level, transparent encryption via JPA `AttributeConverter`s, not a blanket table/database-level encryption scheme. This keeps the change localized to entity definitions and two supporting converter classes, and leaves every other read/write path (repositories, services, controllers) untouched wherever they go through normal JPA entity hydration.

Two code paths bypass normal entity hydration and need explicit handling (Section 3): a native-SQL read used for the chat sidebar, and a native-SQL blind upsert used for email sync.

## 1. Architecture

A new `crypto` package holds:

- **`FieldEncryptor`** — a Spring `@Component` wrapping `org.springframework.security.crypto.encrypt.Encryptors.text(key, salt)` (already transitively available via `spring-security-crypto`, no new dependency). Exposes:
  ```java
  public String encrypt(String plaintext) // null-safe: null in, null out
  public String decrypt(String ciphertext) // null-safe: null in, null out
  ```
  Backed by two new properties: `hermes.encryption.key` and `hermes.encryption.salt`, both required, sourced from environment variables (matching the existing convention for other secrets in `application.properties`).

- **`EncryptedStringConverter`** — `AttributeConverter<String, String>`, `@Convert(converter = EncryptedStringConverter.class)`, delegates to `FieldEncryptor`. Used for all `String` fields being encrypted.

- **`EncryptedDoubleConverter`** — `AttributeConverter<Double, String>`, same delegation pattern but parses/formats the `Double` on the way in/out. Used for `latitude`/`longitude`.

Both converters are Spring-managed beans (`autoApply = false`, referenced explicitly per-field) so they can be injected with `FieldEncryptor` via Hibernate's `SpringBeanContainer`.

## 2. Field-by-field scope

| Entity | Field(s) | Converter | Column change needed |
|---|---|---|---|
| `ChatMessageEntity` | `content` | `EncryptedStringConverter` | `TEXT` stays `TEXT` |
| `NotificationEntity` | `title`, `body` | `EncryptedStringConverter` | `title` grows `VARCHAR(500)`→`TEXT`; `body` stays `TEXT` |
| `UserProfileEntity` | `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `email` | `EncryptedStringConverter` | all grow to `TEXT` |
| `UserProfileEntity` | `latitude`, `longitude` | `EncryptedDoubleConverter` | `DOUBLE PRECISION` → `TEXT` |
| `AgentTaskEntity` | `name` | `EncryptedStringConverter` | `VARCHAR`→`TEXT` |
| `AgentTaskEntity` | `payload` | `EncryptedStringConverter` | drops `@JdbcTypeCode(SqlTypes.JSON)`, `JSONB`→`TEXT` |

Explicitly NOT encrypted: `NotificationEntity.listingIds`, `AgentTaskEntity.type/status/schedule`, all `listings.*` columns (public data, and `latitude`/`longitude` there must stay queryable by PostGIS `ST_DWithin` across many rows — unlike `user_profiles`, which is only ever looked up by `userId`).

## 3. Special-case fixes for native-SQL bypasses

One existing native query bypasses `AttributeConverter`s entirely (converters only apply during normal entity hydration) and must be fixed alongside the schema change:

**`ChatMessageRepository.findSessionSummariesByUserId`** currently runs one native SQL projection that reads `content` directly — after encryption this would return ciphertext into `ChatSessionProjection.titleSource`. Replace it with an entity-hydrating approach: a derived-query method fetching each session's message rows via real `ChatMessageEntity` (going through the converter, so `content` decrypts normally), with the existing `ChatHistoryService.truncateTitle(...)` logic unchanged — it just now receives plaintext instead of ciphertext-tainted input. This is a service-layer reshuffle, not a query-semantics change: same 50-session limit, same "first USER message as title source" rule, same ordering.

`UserProfileRepository.upsertEmail`, previously a native blind `INSERT ... ON CONFLICT DO UPDATE`, is replaced entirely rather than patched — this removes the second native-SQL bypass instead of working around it. JPQL (unlike native SQL) applies `AttributeConverter`s during translation, so a JPQL bulk update encrypts `email` transparently, with no manual `FieldEncryptor` call in the service:

```java
@Modifying
@Query("UPDATE UserProfileEntity u SET u.email = :email WHERE u.userId = :userId")
int updateEmail(@Param("userId") UUID userId, @Param("email") String email);
```

`UserProfileService.syncEmail` tries the update first; if no row existed yet (0 rows affected), it inserts a new profile row via a normal JPA `save`:

```java
@Transactional
public void syncEmail(UUID userId, String email) {
    if (email == null || email.isBlank()) return;
    int updated = userProfileRepository.updateEmail(userId, email);
    if (updated == 0) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setEmail(email);
        entity.setUpdatedAt(Instant.now());
        try {
            userProfileRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent syncEmail call for the same new user — the row
            // now exists, so retry as an update instead of failing the request.
            userProfileRepository.updateEmail(userId, email);
        }
    }
}
```

The update-then-maybe-insert sequence isn't atomic the way `ON CONFLICT` was, so two concurrent `syncEmail` calls for a brand-new user (e.g. the same user open in two tabs) could both see 0 rows updated and both attempt an insert; the second insert hits the `user_profiles` primary key constraint. The catch block treats that specific case as "someone else just created the row" and retries as an update, so the caller never sees a failure. This is a rare race (same user, first-ever sync, near-simultaneous requests) and the guard is a plain retry, not a new dependency or query.

No other native queries in the codebase touch an encrypted column (confirmed by inspection of `NotificationRepository` and `AgentTaskRepository`).

## 4. Migration and testing

A single new Flyway migration, `V15__encrypt_sensitive_columns.sql`:
- `TRUNCATE` the four affected tables (`chat_messages`, `notifications`, `user_profiles`, `agent_tasks`) — existing dev data is disposable and can't be transparently re-encrypted by a SQL migration anyway.
- `ALTER COLUMN ... TYPE TEXT` for every column listed in the table above.
- Drop the `JSONB` typing on `agent_tasks.payload` (column becomes plain `TEXT`; `AgentTaskEntity.payload`'s `@JdbcTypeCode(SqlTypes.JSON)` annotation is removed to match).

`src/test/resources/application.properties` gets fixed test values for `hermes.encryption.key`/`hermes.encryption.salt` so encrypt/decrypt round-trips work in unit and Testcontainers-based tests. Because converters are transparent at the entity level, existing tests that assert on these fields' values (`ChatMessageRepositoryTest`, `UserProfileServiceTest`, `AgentTaskServiceTest`, etc.) need no changes. `UserProfileRepositoryUpsertEmailTest` is renamed/rewritten to exercise `updateEmail` + the insert fallback (no more native-upsert-specific assertions, since the new path is plain JPQL/JPA); it can drop its Testcontainers/Postgres dependency and run against the default H2 `@DataJpaTest` setup, since JPQL bulk updates and JPA saves are portable across databases — unlike the old `ON CONFLICT` syntax, which was Postgres-only. The only remaining test that asserts on **raw SQL/JDBC** results for an affected column is the chat-session-title query (being reworked in this same design, Section 3).

## Out of scope

- Encrypting `listings.*` columns (public scraped data, and lat/lon there must remain queryable for PostGIS spatial search).
- Encrypting `AgentTaskEntity.type`, `status`, `schedule`, or `NotificationEntity.listingIds` — none carry user-authored content.
- Any change to the PostGIS radius-search code path — it operates exclusively on the unencrypted `listings` table.
- Key rotation / re-encryption tooling — out of scope for this initial pass; a future concern once the columns exist.
- Preserving existing `chat_messages`/`notifications`/`user_profiles`/`agent_tasks` data across the migration (explicitly wiped per user's choice).

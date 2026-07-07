# Data Encryption for Sensitive Fields — Design Spec

**Date:** 2026-07-06
**Status:** Approved

## Overview

Encrypts sensitive user-generated data at rest — chat messages, notifications, user profile (including address/geocode), and agent task payloads — without disrupting existing functionality (radius search, email sending, chat history sidebar).

The scope is column-level, transparent encryption via JPA `AttributeConverter`s, not a blanket table/database-level encryption scheme. This keeps the change localized to entity definitions and two supporting converter classes, and leaves every other read/write path (repositories, services, controllers) untouched wherever they go through normal JPA entity hydration.

Two code paths bypass normal entity hydration and need explicit handling (Section 4): a native-SQL read used for the chat sidebar, and a native-SQL blind upsert used for email sync.

## 1. Architecture

A new `crypto` package holds:

- **`FieldEncryptor`** — a Spring `@Component` wrapping one `org.springframework.security.crypto.encrypt.Encryptors.text(key, salt)` instance per configured key version (already transitively available via `spring-security-crypto`, no new dependency). Exposes:
  ```java
  public String encrypt(String plaintext) // null-safe: null in, null out
  public String decrypt(String ciphertext) // null-safe: null in, null out
  ```
  Backed by indexed properties `hermes.encryption.keys.<n>` / `hermes.encryption.salts.<n>` (one pair per key version, `n` starting at `1`) plus `hermes.encryption.current-version`, all required, sourced from environment variables (matching the existing convention for other secrets in `application.properties`). At startup, `FieldEncryptor` builds a `Map<Integer, TextEncryptor>` from every configured version. `encrypt` always uses the `current-version` encryptor and prefixes its output with the version, e.g. `2:<hex>`. `decrypt` parses the leading version prefix and looks up the matching encryptor from the map — see [Key versioning and rotation](#3-key-versioning-and-rotation) below.

  Note: `Encryptors.text(key, salt)`'s underlying `AesBytesEncryptor` already generates a random 16-byte IV per call and prepends it to the ciphertext bytes before hex-encoding the result into one string. So each stored value already carries its own IV inline — no separate IV column or field is needed; this is inherent to the encryptor Spring provides, not something this design has to add.

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

Each of the four tables (`chat_messages`, `notifications`, `user_profiles`, `agent_tasks`) also gains an `encryption_key_version INT NOT NULL DEFAULT 1` column — see [Key versioning and rotation](#3-key-versioning-and-rotation).

Explicitly NOT encrypted: `NotificationEntity.listingIds`, `AgentTaskEntity.type/status/schedule`, all `listings.*` columns (public data, and `latitude`/`longitude` there must stay queryable by PostGIS `ST_DWithin` across many rows — unlike `user_profiles`, which is only ever looked up by `userId`).

## 3. Key versioning and rotation

Rotating `hermes.encryption.keys.*` must not make previously-encrypted data unreadable, and there must be a way to migrate old rows onto the newest key without a flag day. Two independent markers make this possible, each solving a different half of the problem:

**Per-field embedded version (decrypt correctness).** `AttributeConverter`s only see one column at a time — they cannot read a sibling column on the same entity to learn which key version encrypted a given field. So the version travels with the ciphertext itself: `FieldEncryptor.encrypt` prefixes its output with the current version, e.g. `2:<hex-ciphertext-with-embedded-iv>`. `FieldEncryptor.decrypt` parses the prefix and picks the matching `TextEncryptor` from its `Map<Integer, TextEncryptor>`. This keeps every converter fully self-contained — no entity-level lifecycle hooks, no cross-column coupling — while still supporting any number of coexisting key versions.

**Row-level `encryption_key_version` column (cheap auditing/filtering).** This column is *not* consulted for decryption — it exists purely so the re-encryption job (Section 5) can find candidate rows with a plain SQL filter (`WHERE encryption_key_version < :current`) instead of decrypting every row up front to inspect its embedded version. It's stamped via a `@PrePersist`/`@PreUpdate` entity listener that writes `FieldEncryptor`'s current version constant whenever a row is inserted or updated. Because all encrypted fields on a row are always written together using the current key at save time, the row-level value and the per-field embedded versions stay in agreement in normal operation; the re-encryption job still re-derives ground truth per field via `decrypt`, so a theoretical mismatch is harmless.

**Multi-key configuration.** `hermes.encryption.keys.<n>` and `hermes.encryption.salts.<n>` are indexed properties, one pair per key version (`n` starting at `1`), plus `hermes.encryption.current-version` naming which one is active for new writes. All prior versions must stay configured for as long as any row might still carry that version's prefix — removing a version's key/salt before every row referencing it has been re-encrypted makes that data permanently undecryptable. `src/test/resources/application.properties` needs at least two versions configured so rotation and mixed-version decryption can be exercised in tests.

To rotate a key: add a new `hermes.encryption.keys.<n+1>`/`salts.<n+1>` pair, bump `hermes.encryption.current-version`, deploy, then run the re-encryption job (Section 5) to migrate existing rows off the old version. Once no row references an old version (confirmed via the `encryption_key_version` column), its key/salt properties can be safely removed in a later deploy.

## 4. Special-case fixes for native-SQL bypasses

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

## 5. Re-encryption tooling

After a key rotation (Section 3), rows written under an old version need to be migrated onto the current one. This is an admin-triggered batch job, not a standing scheduled process — rotations are rare, operator-initiated events, and a continuous sweep would just be idle most of the time.

**Trigger.** A `CommandLineRunner` gated behind a flag (e.g. `--reencrypt` / a dedicated Spring profile), run manually by an operator after bumping `hermes.encryption.current-version` and redeploying. Not exposed as a public HTTP endpoint.

**Mechanics, per encrypted table:**
1. Page through rows where `encryption_key_version < current-version` (the cheap SQL filter the row-level column exists for), in fixed-size batches (e.g. 500 rows) via a derived query (`findByEncryptionKeyVersionLessThan`), to bound memory and transaction size.
2. For each row, load it as a normal JPA entity — `AttributeConverter`s transparently decrypt every field using its embedded old-version prefix.
3. Issue an explicit `@Modifying` JPQL bulk update for that row, passing every encrypted field's *decrypted* plaintext back in as bind parameters, alongside `current-version` for `encryption_key_version`.

Step 3 is deliberately not "re-set the field on the loaded entity and `save()`, relying on dirty-checking" — that doesn't work here. Hibernate's dirty-checking compares an entity's current in-memory attribute value against the snapshot it loaded, and both are the same decrypted `String`/`Double`; re-setting a field to its own value looks unchanged, so a plain `save()` would silently skip the `UPDATE` and the row would stay on the old key version forever. The JPQL bulk update sidesteps this: Hibernate applies each field's `AttributeConverter` to the bind parameter during query translation, so passing back the plaintext re-encrypts it under whatever `FieldEncryptor` currently considers current — no separate encrypt call needed in the job itself, and no dependence on entity-level change detection.

Each batch commits in its own transaction so a failure partway through a table only needs to resume from the last committed page (rows already re-encrypted no longer match the `encryption_key_version < current-version` filter), not restart the whole table.

**Completion check.** Rotation is done for a given old version once `SELECT count(*) WHERE encryption_key_version = :oldVersion` is `0` across all four tables; only then is it safe to remove that version's `hermes.encryption.keys.<n>`/`salts.<n>` properties.

## 6. Migration and testing

Two Flyway migrations, not one — and not split the way you might expect. `ChatMessageRepositoryTest`, `ListingRepositoryGeoTest`, and `ListingRepositoryRadiusTest` are the only repository tests that validate against real Flyway-migrated Postgres (`ddl-auto=validate`); every other encrypted entity's test runs against H2 with Hibernate auto-generating the schema. Hibernate's `ddl-auto=validate` check validates the *entire* entity metamodel for those three tests, not just the table each test's own repository targets — so every encrypted table's schema change has to land in the same migration window as its entity change, not deferred to a single migration at the end, or those three tests break on a table they don't even touch. In practice this means:

- `V15__encrypt_sensitive_columns.sql`: created early (immediately once the schema for `chat_messages`, `notifications`, and `user_profiles` is known, well before `agent_tasks`'s own change is ready). `TRUNCATE`s all four tables, `ALTER COLUMN ... TYPE TEXT` for `notifications`/`user_profiles`'s columns, and adds `encryption_key_version INT NOT NULL DEFAULT 1` to all four tables. `agent_tasks.name`/`payload` are deliberately left untouched here — widening them before `agent_tasks`'s own entity change lands would itself create the same kind of mismatch.
- `V16__widen_agent_tasks_columns.sql`: `ALTER COLUMN ... TYPE TEXT` for `agent_tasks.name`/`payload`, and drops the `JSONB` typing (column becomes plain `TEXT`; `AgentTaskEntity.payload`'s `@JdbcTypeCode(SqlTypes.JSON)` annotation is removed to match). Lands together with the `AgentTaskEntity` change that needs it.

`src/test/resources/application.properties` gets fixed test values for at least two key versions (`hermes.encryption.keys.1`/`.2`, matching `salts.1`/`.2`, and a `current-version`) so encrypt/decrypt round-trips, version-prefix parsing, and mixed-version decryption can all be exercised in unit and Testcontainers-based tests. Because converters are transparent at the entity level, existing tests that assert on these fields' values (`ChatMessageRepositoryTest`, `UserProfileServiceTest`, `AgentTaskServiceTest`, etc.) need no changes. `UserProfileRepositoryUpsertEmailTest` is renamed/rewritten to exercise `updateEmail` + the insert fallback (no more native-upsert-specific assertions, since the new path is plain JPQL/JPA); it can drop its Testcontainers/Postgres dependency and run against the default H2 `@DataJpaTest` setup, since JPQL bulk updates and JPA saves are portable across databases — unlike the old `ON CONFLICT` syntax, which was Postgres-only. The only remaining test that asserts on **raw SQL/JDBC** results for an affected column is the chat-session-title query (being reworked in this same design, Section 4).

New tests specific to this design: `FieldEncryptorTest` covering encrypt/decrypt round-trips per version and decrypt-by-embedded-version-lookup across multiple configured versions; and re-encryption-job tests covering the paging/batch-completion loop and the per-table JPQL bulk-update-and-re-encrypt behavior against an H2-backed repository fixture.

## Out of scope

- Encrypting `listings.*` columns (public scraped data, and lat/lon there must remain queryable for PostGIS spatial search).
- Encrypting `AgentTaskEntity.type`, `status`, `schedule`, or `NotificationEntity.listingIds` — none carry user-authored content.
- Any change to the PostGIS radius-search code path — it operates exclusively on the unencrypted `listings` table.
- Automatic/scheduled key rotation — this design supports rotation via a manually-triggered re-encryption job (Section 5); it does not add a mechanism that rotates keys or triggers re-encryption on its own.
- Preserving existing `chat_messages`/`notifications`/`user_profiles`/`agent_tasks` data across the migration (explicitly wiped per user's choice).

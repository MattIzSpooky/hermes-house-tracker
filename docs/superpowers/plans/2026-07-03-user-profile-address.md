# User Profile & Address Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let every authenticated user view and update their own home address, storing it only when it can be successfully geocoded, as phase 2 of the Keycloak/roles/multi-user initiative.

**Architecture:** A new `profile` backend module (mirroring the existing `favorites` module's shape) owns a `user_profiles` table keyed directly by the JWT `sub`. `GET /api/profile` returns the caller's address (all-null if none saved yet); `PUT /api/profile/address` synchronously geocodes the address via the existing `GeocodingService` and only persists on success, otherwise returns 422 and leaves any previous address untouched. The API follows this codebase's existing API-first convention: an OpenAPI YAML spec generates a `ProfileApi` interface + request/response models via the `openapi-generator-maven-plugin`, and the controller implements that interface. A new `/profile` Angular page (linked from the header username) provides the address form, following the existing template-driven-forms + signal-based-service style used by the scraping page.

**Tech Stack:** Spring Boot 4.0.6 (Java), MapStruct, Flyway, OpenAPI Generator (`spring` generator, `interfaceOnly`), Angular 22 (`FormsModule`/`ngModel`, signals).

## Global Constraints

- The user id for every profile operation comes from the authenticated JWT's `sub` claim (via `CurrentUser`) — never from a request body or path parameter. No endpoint may accept a user id as input.
- An address is only ever persisted if `GeocodingService.geocodeAddress(...)` succeeds. On failure, throw `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ...)` before any write, and leave a previously-saved address unchanged.
- `GET /api/profile` never 404s — a user with no saved address gets an all-null response, not an error.
- No role-based (`@PreAuthorize`) checks — authentication only, matching phase 1's `SecurityConfig` (already requires auth on all of `/api/**`).
- Do not touch `clientId`-based favorites/chat/notifications, add role-based authorization, or build chat history UI — all explicitly out of scope for this phase.
- This plan builds directly on phase 1 (`SecurityConfig`, `CurrentUser`, `SecuredMockMvcTestSupport`), which is implemented but unmerged on this same branch — no need to reproduce it.

---

## Task 1: `user_profiles` table, entity, and repository

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V11__create_user_profiles.sql`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryTest.java`

**Interfaces:**
- Produces: `UserProfileEntity` (fields: `userId: UUID` (`@Id`), `street: String`, `houseNumber: String`, `houseNumberAddition: String`, `zipCode: String`, `city: String`, `province: String`, `latitude: Double`, `longitude: Double`, `updatedAt: Instant`); `UserProfileRepository extends JpaRepository<UserProfileEntity, UUID>`.

- [ ] **Step 1: Write the Flyway migration**

Create `hermes-backend/src/main/resources/db/migration/V11__create_user_profiles.sql`:

```sql
CREATE TABLE user_profiles (
    user_id               UUID PRIMARY KEY,
    street                VARCHAR(255),
    house_number          VARCHAR(50),
    house_number_addition VARCHAR(50),
    zip_code              VARCHAR(20),
    city                  VARCHAR(255),
    province              VARCHAR(255),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 2: Write `UserProfileEntity`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`:

```java
package com.kropholler.dev.hermes.profile;

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

    private String street;
    private String houseNumber;
    private String houseNumberAddition;
    private String zipCode;
    private String city;
    private String province;

    private Double latitude;
    private Double longitude;

    private Instant updatedAt;
}
```

- [ ] **Step 3: Write `UserProfileRepository`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`:

```java
package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {
}
```

- [ ] **Step 4: Write a repository test to confirm the migration and mapping are correct**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryTest.java`:

```java
package com.kropholler.dev.hermes.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserProfileRepositoryTest {

    @Autowired
    UserProfileRepository repository;

    @Test
    void savesAndReloadsAllFields() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setStreet("Dorpstraat");
        entity.setHouseNumber("10");
        entity.setHouseNumberAddition("A");
        entity.setZipCode("1234AB");
        entity.setCity("Utrecht");
        entity.setProvince("Utrecht");
        entity.setLatitude(52.09);
        entity.setLongitude(5.12);
        entity.setUpdatedAt(now);

        repository.saveAndFlush(entity);
        repository.getEntityManager().clear();

        Optional<UserProfileEntity> reloaded = repository.findById(userId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.get().getHouseNumber()).isEqualTo("10");
        assertThat(reloaded.get().getHouseNumberAddition()).isEqualTo("A");
        assertThat(reloaded.get().getZipCode()).isEqualTo("1234AB");
        assertThat(reloaded.get().getCity()).isEqualTo("Utrecht");
        assertThat(reloaded.get().getProvince()).isEqualTo("Utrecht");
        assertThat(reloaded.get().getLatitude()).isEqualTo(52.09);
        assertThat(reloaded.get().getLongitude()).isEqualTo(5.12);
    }
}
```

Note: `@DataJpaTest` uses `spring.jpa.hibernate.ddl-auto=create-drop` from `hermes-backend/src/test/resources/application.properties` (Flyway is disabled in tests via `spring.flyway.enabled=false`), so the migration SQL above is exercised for real when running against the dev/prod profile, while tests validate the entity mapping against a schema Hibernate generates directly from the entity — both must agree on column/table names, which they do here since neither uses custom `@Column` overrides (Hibernate's default snake_case physical naming strategy, already used by every other entity in this codebase, converts `houseNumber` → `house_number` etc. automatically).

- [ ] **Step 5: Run the test**

Run: `mvn test -Dtest=UserProfileRepositoryTest -f hermes-backend/pom.xml`
Expected: PASS, 1 test, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V11__create_user_profiles.sql \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryTest.java
git commit -m "feat(backend): add user_profiles table, entity, and repository"
```

---

## Task 2: `UserProfileService` — get, geocode-and-upsert-or-reject

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/AddressDto.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`

**Interfaces:**
- Consumes: `UserProfileRepository` (Task 1); `com.kropholler.dev.hermes.listing.geocoding.GeocodingService.geocodeAddress(String houseNumber, String street, String city): Optional<GeocodeResult>`; `com.kropholler.dev.hermes.listing.geocoding.GeocodeResult` (record with `lon(): double`, `lat(): double`).
- Produces: `AddressDto` (record: `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `latitude: Double`, `longitude: Double`); `UserProfileService.getProfile(UUID userId): AddressDto`; `UserProfileService.updateAddress(UUID userId, String street, String houseNumber, String houseNumberAddition, String zipCode, String city, String province): AddressDto` (throws `ResponseStatusException` with status 422 if the address cannot be geocoded).

- [ ] **Step 1: Write `AddressDto`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/AddressDto.java`:

```java
package com.kropholler.dev.hermes.profile;

public record AddressDto(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Double latitude,
    Double longitude
) {
    static AddressDto empty() {
        return new AddressDto(null, null, null, null, null, null, null, null);
    }
}
```

- [ ] **Step 2: Write the failing test for `getProfile`**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    UserProfileRepository repository;
    @Mock
    GeocodingService geocodingService;

    UserProfileService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new UserProfileService(repository, geocodingService);
    }

    @Test
    void getProfile_returnsAllNullWhenNoRowExists() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        AddressDto result = service.getProfile(userId);

        assertThat(result.street()).isNull();
        assertThat(result.houseNumber()).isNull();
        assertThat(result.city()).isNull();
        assertThat(result.latitude()).isNull();
        assertThat(result.longitude()).isNull();
    }

    @Test
    void getProfile_returnsMappedDtoWhenRowExists() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setStreet("Dorpstraat");
        entity.setHouseNumber("10");
        entity.setCity("Utrecht");
        entity.setLatitude(52.09);
        entity.setLongitude(5.12);
        when(repository.findById(userId)).thenReturn(Optional.of(entity));

        AddressDto result = service.getProfile(userId);

        assertThat(result.street()).isEqualTo("Dorpstraat");
        assertThat(result.houseNumber()).isEqualTo("10");
        assertThat(result.city()).isEqualTo("Utrecht");
        assertThat(result.latitude()).isEqualTo(52.09);
        assertThat(result.longitude()).isEqualTo(5.12);
    }

    @Test
    void updateAddress_savesGeocodedAddressOnSuccess() {
        UUID userId = UUID.randomUUID();
        GeocodeResult geocodeResult = new GeocodeResult(5.12, 52.09, List.of("52.0", "52.2", "5.0", "5.2"));
        when(geocodingService.geocodeAddress("10", "Dorpstraat", "Utrecht"))
            .thenReturn(Optional.of(geocodeResult));
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddressDto result = service.updateAddress(
            userId, "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht");

        assertThat(result.street()).isEqualTo("Dorpstraat");
        assertThat(result.latitude()).isEqualTo(52.09);
        assertThat(result.longitude()).isEqualTo(5.12);

        ArgumentCaptor<UserProfileEntity> cap = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        assertThat(cap.getValue().getStreet()).isEqualTo("Dorpstraat");
        assertThat(cap.getValue().getLatitude()).isEqualTo(52.09);
        assertThat(cap.getValue().getLongitude()).isEqualTo(5.12);
        assertThat(cap.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void updateAddress_updatesExistingRowInPlace() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Oude straat");
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        GeocodeResult geocodeResult = new GeocodeResult(5.12, 52.09, List.of("52.0", "52.2", "5.0", "5.2"));
        when(geocodingService.geocodeAddress("10", "Dorpstraat", "Utrecht"))
            .thenReturn(Optional.of(geocodeResult));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateAddress(userId, "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht");

        ArgumentCaptor<UserProfileEntity> cap = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue()).isSameAs(existing);
        assertThat(cap.getValue().getStreet()).isEqualTo("Dorpstraat");
    }

    @Test
    void updateAddress_throws422AndNeverSavesWhenGeocodingFails() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("999", "Nonexistent Street", "Nowhereville"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAddress(
                userId, "Nonexistent Street", "999", null, "0000ZZ", "Nowhereville", "Utrecht"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("could not be geocoded");

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn test -Dtest=UserProfileServiceTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `UserProfileService` does not exist.

- [ ] **Step 4: Write `UserProfileService`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
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

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=UserProfileServiceTest -f hermes-backend/pom.xml`
Expected: PASS, 5 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/AddressDto.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java
git commit -m "feat(backend): add UserProfileService with geocode-or-reject address updates"
```

---

## Task 3: OpenAPI spec, codegen wiring, and `CurrentUser.current()`

**Files:**
- Create: `hermes-backend/src/main/resources/openapi/profile.yaml`
- Modify: `hermes-backend/pom.xml:445-447` (insert a new `openapi-generator-maven-plugin` execution before the closing `</executions>`)
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java`

**Interfaces:**
- Produces (generated at build time into `com.kropholler.dev.hermes.profile.openapi`): `ProfileApi` interface with `getProfile(): ResponseEntity<AddressResponse>` and `updateAddress(@Valid @RequestBody UpdateAddressRequest): ResponseEntity<AddressResponse>`; `AddressResponse` (fields: `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `latitude: Double`, `longitude: Double`); `UpdateAddressRequest` (same address fields, `street`/`houseNumber`/`city` required, no lat/lon).
- Produces: `CurrentUser.current(): CurrentUser` — a new static method reading the `Jwt` principal from `SecurityContextHolder`.

- [ ] **Step 1: Write the OpenAPI spec**

Create `hermes-backend/src/main/resources/openapi/profile.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Hermes Profile API
  version: 1.0.0

paths:
  /api/profile:
    get:
      operationId: getProfile
      tags: [Profile]
      responses:
        '200':
          description: The caller's saved address (all fields null if none saved yet)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AddressResponse'

  /api/profile/address:
    put:
      operationId: updateAddress
      tags: [Profile]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateAddressRequest'
      responses:
        '200':
          description: Address saved
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AddressResponse'
        '422':
          description: Address could not be geocoded
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

components:
  schemas:
    AddressResponse:
      type: object
      properties:
        street:
          type: string
          nullable: true
        houseNumber:
          type: string
          nullable: true
        houseNumberAddition:
          type: string
          nullable: true
        zipCode:
          type: string
          nullable: true
        city:
          type: string
          nullable: true
        province:
          type: string
          nullable: true
        latitude:
          type: number
          format: double
          nullable: true
        longitude:
          type: number
          format: double
          nullable: true

    UpdateAddressRequest:
      type: object
      required: [street, houseNumber, city]
      properties:
        street:
          type: string
          example: Dorpstraat
        houseNumber:
          type: string
          example: "10"
        houseNumberAddition:
          type: string
          nullable: true
        zipCode:
          type: string
          nullable: true
        city:
          type: string
          example: Utrecht
        province:
          type: string
          nullable: true

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

- [ ] **Step 2: Wire the codegen execution into `pom.xml`**

In `hermes-backend/pom.xml`, find the `openapi-generator-maven-plugin`'s `<executions>` block. The last execution is `generate-notifications`, immediately followed by `</executions>` (around line 446-447). Insert a new execution immediately before that closing tag:

```xml
                    <execution>
                        <id>generate-profile</id>
                        <goals><goal>generate</goal></goals>
                        <configuration>
                            <inputSpec>${project.basedir}/src/main/resources/openapi/profile.yaml</inputSpec>
                            <generatorName>spring</generatorName>
                            <apiPackage>com.kropholler.dev.hermes.profile.openapi</apiPackage>
                            <modelPackage>com.kropholler.dev.hermes.profile.openapi</modelPackage>
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

- [ ] **Step 3: Generate sources and confirm the new API/model classes exist**

Run: `mvn generate-sources -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS, and the following files exist:
- `hermes-backend/target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/profile/openapi/ProfileApi.java`
- `hermes-backend/target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/profile/openapi/AddressResponse.java`
- `hermes-backend/target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/profile/openapi/UpdateAddressRequest.java`

- [ ] **Step 4: Write the failing test for `CurrentUser.current()`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java`, add this test method inside the existing `CurrentUserTest` class (alongside the two tests already there from phase 1):

```java
    @Test
    void current_readsJwtFromSecurityContext() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            CurrentUser currentUser = CurrentUser.current();

            assertThat(currentUser.id()).isEqualTo(subject);
            assertThat(currentUser.username()).isEqualTo("testuser");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn test -Dtest=CurrentUserTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `CurrentUser.current()` does not exist.

- [ ] **Step 6: Add `current()` to `CurrentUser`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java`, add the import `import org.springframework.security.core.context.SecurityContextHolder;` and this static method to the `CurrentUser` record body, alongside the existing `from(Jwt jwt)` method:

```java
    public static CurrentUser current() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return from(jwt);
    }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn test -Dtest=CurrentUserTest -f hermes-backend/pom.xml`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 8: Confirm the module structure still verifies (spring-modulith)**

Run: `mvn test -Dtest=HermesBackendApplicationTests -f hermes-backend/pom.xml`
Expected: PASS — the new `profile` module's dependency on `com.kropholler.dev.hermes.listing.geocoding` does not violate module boundaries (no `package-info.java` in this codebase restricts that package to internal-only access).

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/profile.yaml \
        hermes-backend/pom.xml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java
git commit -m "feat(backend): add profile OpenAPI spec/codegen and CurrentUser.current()"
```

---

## Task 4: `UserProfileApiMapper` and `UserProfileController`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileApiMapper.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java`

**Interfaces:**
- Consumes: `AddressDto`, `UserProfileService` (Task 2); `ProfileApi`, `AddressResponse`, `UpdateAddressRequest` (Task 3, generated); `CurrentUser.current()` (Task 3); `com.kropholler.dev.hermes.config.SecurityConfig`, `com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport` (phase 1).
- Produces: `UserProfileController` implementing `ProfileApi`, backing `GET /api/profile` and `PUT /api/profile/address`.

- [ ] **Step 1: Write `UserProfileApiMapper`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileApiMapper.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.profile.openapi.AddressResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserProfileApiMapper {

    AddressResponse toResponse(AddressDto dto);
}
```

- [ ] **Step 2: Write `UserProfileController`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.profile.openapi.AddressResponse;
import com.kropholler.dev.hermes.profile.openapi.ProfileApi;
import com.kropholler.dev.hermes.profile.openapi.UpdateAddressRequest;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserProfileController implements ProfileApi {

    private final UserProfileService userProfileService;
    private final UserProfileApiMapper userProfileApiMapper;

    @Override
    public ResponseEntity<AddressResponse> getProfile() {
        AddressDto dto = userProfileService.getProfile(CurrentUser.current().id());
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }

    @Override
    public ResponseEntity<AddressResponse> updateAddress(UpdateAddressRequest request) {
        AddressDto dto = userProfileService.updateAddress(
            CurrentUser.current().id(),
            request.getStreet(),
            request.getHouseNumber(),
            request.getHouseNumberAddition(),
            request.getZipCode(),
            request.getCity(),
            request.getProvince()
        );
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }
}
```

- [ ] **Step 3: Write `UserProfileControllerTest`**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java`:

```java
package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserProfileService userProfileService;
    @MockitoBean UserProfileApiMapper userProfileApiMapper;

    @Test
    void getProfile_usesSubjectFromJwtNotFromRequest() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = AddressDto.empty();
        when(userProfileService.getProfile(subject)).thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(get("/api/profile")
                .with(jwt().jwt(builder -> builder.subject(subject.toString()))))
            .andExpect(status().isOk());

        verify(userProfileService).getProfile(eq(subject));
    }

    @Test
    void updateAddress_usesSubjectFromJwtNotFromRequest() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = new AddressDto("Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht", 52.09, 5.12);
        when(userProfileService.updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht")))
            .thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString())))
                .contentType("application/json")
                .content("""
                    {"street":"Dorpstraat","houseNumber":"10","zipCode":"1234AB","city":"Utrecht","province":"Utrecht"}
                    """))
            .andExpect(status().isOk());

        verify(userProfileService).updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht"));
    }

    @Test
    void updateAddress_returns422WhenGeocodingFails() throws Exception {
        UUID subject = UUID.randomUUID();
        when(userProfileService.updateAddress(eq(subject), eq("Nonexistent"), eq("999"), eq(null), eq(null), eq("Nowhereville"), eq(null)))
            .thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Address could not be geocoded"));

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString())))
                .contentType("application/json")
                .content("""
                    {"street":"Nonexistent","houseNumber":"999","city":"Nowhereville"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Address could not be geocoded"));
    }
}
```

- [ ] **Step 4: Run the test**

Run: `mvn test -Dtest=UserProfileControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 5: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS, all tests pass (411 from phase 1 + new tests from this plan's Tasks 1-4, no regressions).

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileApiMapper.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java
git commit -m "feat(backend): add UserProfileController wired to the authenticated caller's identity"
```

---

## Task 5: Frontend — `ProfileService` and types

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`
- Create: `hermes-frontend/src/app/core/profile.service.ts`
- Test: `hermes-frontend/src/app/core/profile.service.spec.ts`

**Interfaces:**
- Produces: `AddressResponse` and `UpdateAddressRequest` TypeScript interfaces; `ProfileService` with `readonly address: Signal<AddressResponse | null>`, `readonly loading: Signal<boolean>`, `readonly error: Signal<string | null>`, `loadProfile(): void`, `updateAddress(req: UpdateAddressRequest): void`.

- [ ] **Step 1: Add types to `api.types.ts`**

In `hermes-frontend/src/app/core/api.types.ts`, add these interfaces (anywhere among the other `interface` declarations, e.g. after `GeocodingBackfillResponse`):

```ts
export interface AddressResponse {
  street?: string | null;
  houseNumber?: string | null;
  houseNumberAddition?: string | null;
  zipCode?: string | null;
  city?: string | null;
  province?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

export interface UpdateAddressRequest {
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode?: string;
  city: string;
  province?: string;
}
```

- [ ] **Step 2: Write the failing test for `ProfileService`**

Create `hermes-frontend/src/app/core/profile.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfileService } from './profile.service';
import { AddressResponse } from './api.types';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loadProfile sets address on success', () => {
    const response: AddressResponse = { street: 'Dorpstraat', city: 'Utrecht' };

    service.loadProfile();
    const req = httpMock.expectOne('/api/profile');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.address()).toEqual(response);
    expect(service.loading()).toBe(false);
  });

  it('updateAddress sets error message on 422', () => {
    service.updateAddress({ street: 'X', houseNumber: '1', city: 'Nowhere' });
    const req = httpMock.expectOne('/api/profile/address');
    expect(req.request.method).toBe('PUT');
    req.flush(
      { detail: 'Address could not be geocoded' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(service.error()).toBe('Address could not be geocoded');
    expect(service.loading()).toBe(false);
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `profile.service.ts` does not exist yet (compilation error for this spec file).

- [ ] **Step 4: Write `ProfileService`**

Create `hermes-frontend/src/app/core/profile.service.ts`:

```ts
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AddressResponse, UpdateAddressRequest } from './api.types';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  readonly address = signal<AddressResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadProfile(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<AddressResponse>('/api/profile').subscribe({
      next: data => {
        this.address.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to load profile');
        this.loading.set(false);
      },
    });
  }

  updateAddress(req: UpdateAddressRequest): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.put<AddressResponse>('/api/profile/address', req).subscribe({
      next: data => {
        this.address.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to save address');
        this.loading.set(false);
      },
    });
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `ProfileService` spec's 2 tests pass (pre-existing unrelated chat-component failures from phase 1 are still present and are not a regression from this task).

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/core/api.types.ts \
        hermes-frontend/src/app/core/profile.service.ts \
        hermes-frontend/src/app/core/profile.service.spec.ts
git commit -m "feat(frontend): add ProfileService and address types"
```

---

## Task 6: Frontend — profile page, route, and header link

**Files:**
- Create: `hermes-frontend/src/app/pages/profile/profile-page.component.ts`
- Create: `hermes-frontend/src/app/pages/profile/profile-page.component.html`
- Test: `hermes-frontend/src/app/pages/profile/profile-page.component.spec.ts`
- Modify: `hermes-frontend/src/app/app.routes.ts`
- Modify: `hermes-frontend/src/app/app.component.html`

**Interfaces:**
- Consumes: `ProfileService`, `AddressResponse`, `UpdateAddressRequest` (Task 5); `authGuard` (phase 1); `ErrorAlertComponent`, `SectionCardComponent`, `SpinnerComponent` (existing shared components).

- [ ] **Step 1: Write `ProfilePageComponent`**

The form fields are plain component properties bound via `ngModel` (matching `ScrapingPageComponent`'s style). Since `loadProfile()` resolves asynchronously, an `effect()` registered in the constructor patches the form fields whenever `svc.address()` changes, rather than a one-shot `ngOnInit` read that could run before the HTTP response arrives.

Create `hermes-frontend/src/app/pages/profile/profile-page.component.ts`:

```ts
import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProfileService } from '../../core/profile.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';
import { SpinnerComponent } from '../../shared/spinner.component';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [FormsModule, ErrorAlertComponent, SectionCardComponent, SpinnerComponent],
  templateUrl: './profile-page.component.html',
})
export class ProfilePageComponent {
  protected readonly svc = inject(ProfileService);

  street = '';
  houseNumber = '';
  houseNumberAddition = '';
  zipCode = '';
  city = '';
  province = '';

  constructor() {
    this.svc.loadProfile();
    effect(() => {
      const address = this.svc.address();
      if (address) {
        this.street = address.street ?? '';
        this.houseNumber = address.houseNumber ?? '';
        this.houseNumberAddition = address.houseNumberAddition ?? '';
        this.zipCode = address.zipCode ?? '';
        this.city = address.city ?? '';
        this.province = address.province ?? '';
      }
    });
  }

  submit(): void {
    if (!this.street || !this.houseNumber || !this.city) return;
    this.svc.updateAddress({
      street: this.street,
      houseNumber: this.houseNumber,
      city: this.city,
      ...(this.houseNumberAddition && { houseNumberAddition: this.houseNumberAddition }),
      ...(this.zipCode && { zipCode: this.zipCode }),
      ...(this.province && { province: this.province }),
    });
  }
}
```

- [ ] **Step 2: Write the template**

Create `hermes-frontend/src/app/pages/profile/profile-page.component.html`:

```html
<div class="mb-6">
  <h1 class="text-2xl font-bold text-slate-900">Your profile</h1>
  <p class="text-sm text-slate-500 mt-0.5">Manage your saved address</p>
</div>

@if (svc.error()) {
  <app-error-alert [message]="svc.error()!" />
}

<app-section-card padding="p-6" extraClass="max-w-lg">
  <form (ngSubmit)="submit()" class="space-y-5">
    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">
        Street <span class="text-cyan-500">*</span>
      </label>
      <input type="text" [(ngModel)]="street" name="street" required
        class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
               focus:border-cyan-500 focus:ring-cyan-500" />
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">
          House number <span class="text-cyan-500">*</span>
        </label>
        <input type="text" [(ngModel)]="houseNumber" name="houseNumber" required
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Addition</label>
        <input type="text" [(ngModel)]="houseNumberAddition" name="houseNumberAddition"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500" />
      </div>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Zip code</label>
        <input type="text" [(ngModel)]="zipCode" name="zipCode"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">
          City <span class="text-cyan-500">*</span>
        </label>
        <input type="text" [(ngModel)]="city" name="city" required
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500" />
      </div>
    </div>

    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">Province</label>
      <input type="text" [(ngModel)]="province" name="province"
        class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
               focus:border-cyan-500 focus:ring-cyan-500" />
    </div>

    <button type="submit" [disabled]="!street || !houseNumber || !city || svc.loading()"
      class="w-full rounded-lg bg-cyan-500 px-4 py-2.5 text-sm font-semibold text-white
             hover:bg-cyan-400 disabled:opacity-50 transition-colors">
      @if (svc.loading()) {
        <app-spinner color="white" label="Saving..." />
      } @else {
        Save address
      }
    </button>
  </form>
</app-section-card>
```

- [ ] **Step 3: Write a focused component spec**

Create `hermes-frontend/src/app/pages/profile/profile-page.component.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('patches form fields from the loaded profile', () => {
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/profile');
    req.flush({ street: 'Dorpstraat', houseNumber: '10', city: 'Utrecht' });
    fixture.detectChanges();

    expect(fixture.componentInstance.street).toBe('Dorpstraat');
    expect(fixture.componentInstance.houseNumber).toBe('10');
    expect(fixture.componentInstance.city).toBe('Utrecht');
  });
});
```

- [ ] **Step 4: Add the route**

In `hermes-frontend/src/app/app.routes.ts`, add a new route (after the `watches` route, before the closing `];`):

```ts
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/profile/profile-page.component').then(
        m => m.ProfilePageComponent
      ),
  },
```

- [ ] **Step 5: Link the header username to the profile page**

In `hermes-frontend/src/app/app.component.html`, change the username `<span>` to a `routerLink`:

```html
          <a routerLink="/profile"
             class="text-slate-300 hover:text-white text-sm">{{ username }}</a>
```

(replacing the existing `<span class="text-slate-300 text-sm">{{ username }}</span>` inside the `@if (username) { ... }` block added in phase 1.)

- [ ] **Step 6: Run the frontend build and test suite**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: the new `ProfilePageComponent` spec passes; total pass count increases by the specs added in this plan (Tasks 5-6), with the same pre-existing unrelated chat-component failures from phase 1 (not a regression).

- [ ] **Step 7: Commit**

```bash
git add hermes-frontend/src/app/pages/profile/ \
        hermes-frontend/src/app/app.routes.ts \
        hermes-frontend/src/app/app.component.html
git commit -m "feat(frontend): add profile page for viewing/updating the user's address"
```

---

## Task 7: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Log in and save a real address**

Open `http://localhost:4200`, log in as `testuser` / `password`, click the username in the header to navigate to `/profile`. Enter a real, geocodable address (e.g. street `Rentmeesterlaan`, house number `9`, city `Weert`) and save.

Expected: the form shows a saved state (no error), and reloading `/profile` shows the same address persisted.

- [ ] **Step 3: Confirm an ungeocodable address is rejected without touching the saved one**

On the same page, change the address to nonsense (e.g. street `Zzzznotarealstreet`, house number `99999`, city `Nonexistentville`) and save.

Expected: an inline error appears (via `app-error-alert`) stating the address could not be geocoded. Reload `/profile` and confirm the previously-saved real address from Step 2 is still there, untouched.

- [ ] **Step 4: Confirm `GET /api/profile` never 404s for a brand-new user**

Log in as `testadmin` / `password` (who has never saved an address) and visit `/profile`.

Expected: the form loads with all fields empty (no error), confirming the "no row yet" case returns a valid empty response rather than a 404.

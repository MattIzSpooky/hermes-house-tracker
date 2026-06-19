# Agentic AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a proactive agentic layer — listing watches, on-demand research, and market digests — unified under a single `AgentTask` execution loop with in-app WebSocket and email notification delivery.

**Architecture:** A single `AgentTaskScheduler` ticks every minute and dispatches due `AgentTask` rows to type-specific handlers (`WatchTaskHandler`, `ResearchTaskHandler`, `DigestTaskHandler`). All three handlers produce a `NotificationContent` that `NotificationService` persists, pushes over STOMP, and emails. Three new chat tools (`SaveWatchTool`, `TriggerResearchTool`, `ListWatchesTool`) let the AI create and manage tasks from conversation. The Angular frontend gains a notification bell with a live STOMP subscription and a `/watches` page.

**Tech Stack:** Spring Boot 4.0.6, Spring AI 2.0.0-RC1, Ollama, Spring WebSocket (STOMP, already configured), `spring-boot-starter-mail` (JavaMailSender + Gmail SMTP), Flyway, PostgreSQL, Angular 22, `@stomp/stompjs` (already used in `chat.service.ts`), Java 25, Lombok, MapStruct.

## Global Constraints

- Java package root: `com.kropholler.dev.hermes`
- All backend tests run from `hermes-backend/` with `mvnw.cmd test -Dtest=ClassName`
- Next Flyway migration is **V9** (`V8__cities_replace_geometry_with_lat_lon.sql` exists)
- Spring cron format is 6-field (seconds first): `"0 0 8 * * *"` = daily 08:00
- Never mention Funda.nl or external URLs in AI responses
- `@Scheduled` and `@Async` are already enabled via `AsyncConfig`
- WebSocket `/topic` broker prefix already configured in `WebSocketConfig`
- `SimpMessagingTemplate` is already available as a bean (used in `ChatController`)
- All new controllers must implement a generated OpenAPI interface (see existing `ScrapingSessionController` pattern)
- Lombok + MapStruct annotation processor order must be preserved in `pom.xml`

---

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: DB migration V9 + `spring-boot-starter-mail` dependency | ✅ Complete | `00eefbb` |
| Task 2: Domain — AgentTask + Notification entities, repos, DTOs, enums | ✅ Complete | `0b7b055` |
| Task 3: AgentTaskService (public service: create, list, delete, markRan) | ✅ Complete | `6587ab9` |
| Task 4: AgentTaskHandler interface + WatchTaskHandler | ✅ Complete | `03e623a` |
| Task 5: ResearchTaskHandler | ✅ Complete | `8045895` |
| Task 6: DigestTaskHandler | ✅ Complete | `e1b18a1` |
| Task 7: NotificationService (persist + WebSocket push + email) | ✅ Complete | `41c810a` |
| Task 8: AgentTaskExecutor + AgentTaskScheduler | ✅ Complete | `c10cadc` |
| Task 9: OpenAPI additions + AgentTaskController + NotificationController | ✅ Complete | `335b27e` |
| Task 10: Chat tools — SaveWatchTool, TriggerResearchTool, ListWatchesTool | ✅ Complete | `9971f44` |
| Task 11: Frontend — types, NotificationsService, notification bell | ✅ Complete | `7ac14cd` |
| Task 12: Frontend — AgentTaskService + WatchesPage | ✅ Complete | `8b74f7b` |

---

## File Map

### New backend files
| File | Responsibility |
|---|---|
| `hermes-backend/src/main/resources/db/migration/V9__add_agent_tasks_and_notifications.sql` | Tables: `agent_tasks`, `notifications` |
| `…/agent/AgentTaskType.java` | Enum: `WATCH`, `RESEARCH`, `DIGEST` |
| `…/agent/AgentTaskStatus.java` | Enum: `ACTIVE`, `PAUSED`, `COMPLETED` |
| `…/agent/internal/AgentTask.java` | JPA entity |
| `…/agent/internal/AgentTaskRepository.java` | JPA repo |
| `…/agent/internal/Notification.java` | JPA entity |
| `…/agent/internal/NotificationRepository.java` | JPA repo |
| `…/agent/AgentTaskDto.java` | Public DTO record |
| `…/agent/NotificationDto.java` | Public DTO record |
| `…/agent/AgentTaskService.java` | Create/list/delete tasks, `markRan` |
| `…/agent/internal/NotificationContent.java` | Handler return record: title, body, listingIds |
| `…/agent/internal/AgentTaskHandler.java` | Handler interface |
| `…/agent/internal/WatchPayload.java` | Deserialized watch criteria |
| `…/agent/internal/WatchTaskHandler.java` | Finds new listings since `lastRunAt` |
| `…/agent/internal/ResearchPayload.java` | `{ prompt }` |
| `…/agent/internal/ResearchTaskHandler.java` | Non-streaming ChatClient call with all tools |
| `…/agent/internal/DigestPayload.java` | `{ cities }` |
| `…/agent/internal/DigestTaskHandler.java` | AI-narrated city market digest |
| `…/agent/NotificationService.java` | Persist + STOMP push + async email |
| `…/agent/internal/AgentTaskExecutor.java` | Strategy dispatch to handler |
| `…/agent/AgentTaskScheduler.java` | `@Scheduled(fixedDelay=60_000)` tick |
| `…/api/AgentTaskController.java` | `GET /api/agent-tasks`, `DELETE /api/agent-tasks/{id}` |
| `…/api/NotificationController.java` | `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `GET /api/notifications/unread-count` |
| `…/ai/internal/SaveWatchTool.java` | Chat tool: creates WATCH task |
| `…/ai/internal/TriggerResearchTool.java` | Chat tool: queues RESEARCH task |
| `…/ai/internal/ListWatchesTool.java` | Chat tool: lists/cancels WATCH tasks |

### Modified backend files
| File | Change |
|---|---|
| `hermes-backend/pom.xml` | Add `spring-boot-starter-mail` |
| `hermes-backend/src/main/resources/application.properties` | Add mail SMTP properties |
| `hermes-backend/src/main/resources/openapi/api.yaml` | Add agent-tasks + notifications endpoints |
| `…/ai/AiChatService.java` | Inject `AgentTaskService`; add 3 new tools to `startStream` |
| `…/ai/AiConfig.java` | Add 4 lines to `CHAT_SYSTEM_PROMPT` |

### New frontend files
| File | Responsibility |
|---|---|
| `hermes-frontend/src/app/core/notifications.service.ts` | STOMP subscription, unread count signal, notification list |
| `hermes-frontend/src/app/core/agent-task.service.ts` | HTTP: list + delete agent tasks |
| `hermes-frontend/src/app/shared/notification-bell.component.ts` | Badge + slide-out panel |
| `hermes-frontend/src/app/shared/notification-bell.component.html` | Bell icon, unread badge, notification list |
| `hermes-frontend/src/app/pages/watches/watches-page.component.ts` | Table of active watches with delete |
| `hermes-frontend/src/app/pages/watches/watches-page.component.html` | Watches table template |

### Modified frontend files
| File | Change |
|---|---|
| `hermes-frontend/src/app/core/api.types.ts` | Add `AgentTaskResponse`, `NotificationResponse`, `UnreadCountResponse` |
| `hermes-frontend/src/app/app.component.ts` | Import `NotificationBellComponent` |
| `hermes-frontend/src/app/app.component.html` | Add `<app-notification-bell>` to header |
| `hermes-frontend/src/app/app.routes.ts` | Add `/watches` route |

---

## Task 1: DB migration V9 + spring-boot-starter-mail dependency

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V9__add_agent_tasks_and_notifications.sql`
- Modify: `hermes-backend/pom.xml`
- Modify: `hermes-backend/src/main/resources/application.properties`

- [ ] **Step 1: Add spring-boot-starter-mail to pom.xml**

In `hermes-backend/pom.xml`, add after the `spring-boot-starter-websocket` dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

- [ ] **Step 2: Add mail properties to application.properties**

Append to `hermes-backend/src/main/resources/application.properties`:
```properties
# Email notifications (set MAIL_USERNAME and MAIL_PASSWORD env vars for Gmail)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
hermes.notifications.from-email=${MAIL_USERNAME:noreply@hermes.local}
hermes.notifications.to-email=${MAIL_TO:matthijsk2000@gmail.com}
```

- [ ] **Step 3: Write the Flyway migration**

Create `hermes-backend/src/main/resources/db/migration/V9__add_agent_tasks_and_notifications.sql`:
```sql
CREATE TABLE agent_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    client_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    schedule VARCHAR(100),
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES agent_tasks(id) ON DELETE CASCADE,
    client_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    listing_ids JSONB DEFAULT '[]',
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    email_sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_agent_tasks_status_next_run ON agent_tasks(status, next_run_at);
CREATE INDEX idx_notifications_client_id_created ON notifications(client_id, created_at DESC);
```

- [ ] **Step 4: Verify migration applies cleanly**

Start the backend (or run `mvnw.cmd test -Dtest=HermesBackendApplicationTests` from `hermes-backend/`). Confirm Flyway logs show `V9__add_agent_tasks_and_notifications.sql` applied successfully with no errors.

- [ ] **Step 5: Commit**
```bash
git add hermes-backend/pom.xml hermes-backend/src/main/resources/application.properties hermes-backend/src/main/resources/db/migration/V9__add_agent_tasks_and_notifications.sql
git commit -m "feat(agent): add V9 migration for agent_tasks + notifications, add mail dependency"
```

---

## Task 2: Domain — entities, repos, DTOs, enums

**Files:**
- Create: `…/agent/AgentTaskType.java`
- Create: `…/agent/AgentTaskStatus.java`
- Create: `…/agent/internal/AgentTask.java`
- Create: `…/agent/internal/AgentTaskRepository.java`
- Create: `…/agent/internal/Notification.java`
- Create: `…/agent/internal/NotificationRepository.java`
- Create: `…/agent/AgentTaskDto.java`
- Create: `…/agent/NotificationDto.java`
- Test: `…/agent/internal/AgentTaskRepositoryTest.java`

**Interfaces:**
- Produces: `AgentTaskType`, `AgentTaskStatus`, `AgentTask`, `AgentTaskRepository`, `Notification`, `NotificationRepository`, `AgentTaskDto`, `NotificationDto`

- [ ] **Step 1: Write the repository test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/AgentTaskRepositoryTest.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AgentTaskRepositoryTest {

    @Autowired
    AgentTaskRepository repo;

    @Test
    void findsDueActiveTasks() {
        AgentTask task = new AgentTask();
        task.setType(AgentTaskType.WATCH);
        task.setClientId(UUID.randomUUID());
        task.setName("test watch");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().minusSeconds(60));
        repo.save(task);

        List<AgentTask> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getName()).isEqualTo("test watch");
    }

    @Test
    void doesNotReturnFutureTasks() {
        AgentTask task = new AgentTask();
        task.setType(AgentTaskType.RESEARCH);
        task.setClientId(UUID.randomUUID());
        task.setName("future task");
        task.setPayload("{}");
        task.setNextRunAt(Instant.now().plusSeconds(3600));
        repo.save(task);

        List<AgentTask> due = repo.findAllByStatusAndNextRunAtLessThanEqual(
            AgentTaskStatus.ACTIVE, Instant.now());

        assertThat(due).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — expect compile failure (classes missing)**

```
mvnw.cmd test -Dtest=AgentTaskRepositoryTest -DfailIfNoTests=false
```
Expected: compilation error — `AgentTaskRepository`, `AgentTask`, enums not found.

- [ ] **Step 3: Create enums**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/AgentTaskType.java`:
```java
package com.kropholler.dev.hermes.agent;

public enum AgentTaskType { WATCH, RESEARCH, DIGEST }
```

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/AgentTaskStatus.java`:
```java
package com.kropholler.dev.hermes.agent;

public enum AgentTaskStatus { ACTIVE, PAUSED, COMPLETED }
```

- [ ] **Step 4: Create AgentTask entity**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/AgentTask.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
@Getter @Setter @NoArgsConstructor
public class AgentTask {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskStatus status = AgentTaskStatus.ACTIVE;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    private String schedule;
    private Instant lastRunAt;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 5: Create AgentTaskRepository**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/AgentTaskRepository.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findAllByStatusAndNextRunAtLessThanEqual(AgentTaskStatus status, Instant cutoff);
    List<AgentTask> findAllByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, AgentTaskStatus status);
}
```

- [ ] **Step 6: Create Notification entity**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/Notification.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

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
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID taskId;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String listingIds = "[]";

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant emailSentAt;
}
```

- [ ] **Step 7: Create NotificationRepository**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/NotificationRepository.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop50ByClientIdOrderByCreatedAtDesc(UUID clientId);
    long countByClientIdAndReadFalse(UUID clientId);
}
```

- [ ] **Step 8: Create DTOs**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/AgentTaskDto.java`:
```java
package com.kropholler.dev.hermes.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentTaskDto(
    UUID id,
    AgentTaskType type,
    AgentTaskStatus status,
    UUID clientId,
    String name,
    String schedule,
    Instant lastRunAt,
    Instant nextRunAt,
    Instant createdAt
) {}
```

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/NotificationDto.java`:
```java
package com.kropholler.dev.hermes.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    UUID taskId,
    UUID clientId,
    String title,
    String body,
    List<UUID> listingIds,
    boolean read,
    Instant createdAt,
    Instant emailSentAt
) {}
```

- [ ] **Step 9: Run test — expect pass**

```
mvnw.cmd test -Dtest=AgentTaskRepositoryTest
```
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 10: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/ hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/
git commit -m "feat(agent): add AgentTask + Notification domain entities, repos, DTOs"
```

---

## Task 3: AgentTaskService

**Files:**
- Create: `…/agent/AgentTaskService.java`
- Test: `…/agent/AgentTaskServiceTest.java`

**Interfaces:**
- Consumes: `AgentTask`, `AgentTaskRepository`, `AgentTaskType`, `AgentTaskStatus`, `AgentTaskDto`
- Produces: `AgentTaskService.createWatch(UUID clientId, String name, WatchPayload payload) -> AgentTaskDto`, `createResearch(UUID clientId, String prompt) -> AgentTaskDto`, `findByClientId(UUID clientId) -> List<AgentTaskDto>`, `delete(UUID taskId)`, `markRan(AgentTask task)`, `findDueTasks() -> List<AgentTask>`

Note: `WatchPayload`, `ResearchPayload`, `DigestPayload` are defined in Task 4–6. Write `AgentTaskService` using them but they will compile in Task 4.

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/AgentTaskServiceTest.java`:
```java
package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.internal.AgentTask;
import com.kropholler.dev.hermes.agent.internal.AgentTaskRepository;
import com.kropholler.dev.hermes.agent.internal.WatchPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    @Mock AgentTaskRepository repo;
    @Spy ObjectMapper objectMapper;
    @InjectMocks AgentTaskService service;

    @Test
    void createWatchPersistsTaskWithDailySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WatchPayload payload = new WatchPayload("Utrecht", null, null, 400000, 3, null, null, null, null, null);
        service.createWatch(UUID.randomUUID(), "Utrecht 3-bed", payload);

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(repo).save(captor.capture());
        AgentTask saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.WATCH);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * *");
        assertThat(saved.getNextRunAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
    }

    @Test
    void createResearchSetsNullScheduleAndImmediateNextRun() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createResearch(UUID.randomUUID(), "analyse my favourites and recommend one");

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(repo).save(captor.capture());
        AgentTask saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.RESEARCH);
        assertThat(saved.getSchedule()).isNull();
        assertThat(saved.getNextRunAt()).isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void markRanSetsCompletedForOneShot() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTask task = new AgentTask();
        task.setSchedule(null);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.RESEARCH);
        task.setClientId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now());

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.COMPLETED);
        assertThat(task.getLastRunAt()).isNotNull();
    }

    @Test
    void markRanUpdatesNextRunAtForRepeating() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentTask task = new AgentTask();
        task.setSchedule("0 0 8 * * *");
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setType(AgentTaskType.WATCH);
        task.setClientId(UUID.randomUUID());
        task.setName("test");
        task.setNextRunAt(Instant.now().minusSeconds(60));

        service.markRan(task);

        assertThat(task.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
        assertThat(task.getNextRunAt()).isAfter(Instant.now());
    }
}
```

- [ ] **Step 2: Run test — expect compile failure (AgentTaskService + WatchPayload missing)**

```
mvnw.cmd test -Dtest=AgentTaskServiceTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create WatchPayload (needed for compilation)**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/WatchPayload.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

public record WatchPayload(
    String city,
    String province,
    Integer minPrice,
    Integer maxPrice,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    String keywords,
    String nearCity,
    Integer radiusKm
) {}
```

- [ ] **Step 4: Create ResearchPayload and DigestPayload (needed for AgentTaskService)**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/ResearchPayload.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

public record ResearchPayload(String prompt) {}
```

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/DigestPayload.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import java.util.List;

public record DigestPayload(List<String> cities) {}
```

- [ ] **Step 5: Create AgentTaskService**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/AgentTaskService.java`:
```java
package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.internal.*;
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
    public AgentTaskDto createWatch(UUID clientId, String name, WatchPayload payload) {
        AgentTask task = new AgentTask();
        task.setType(AgentTaskType.WATCH);
        task.setClientId(clientId);
        task.setName(name);
        task.setPayload(serialize(payload));
        task.setSchedule("0 0 8 * * *");
        task.setNextRunAt(computeNext("0 0 8 * * *"));
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskDto createResearch(UUID clientId, String prompt) {
        AgentTask task = new AgentTask();
        task.setType(AgentTaskType.RESEARCH);
        task.setClientId(clientId);
        task.setName("Research: " + prompt.substring(0, Math.min(60, prompt.length())));
        task.setPayload(serialize(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskDto createDigest(UUID clientId, String name, List<String> cities) {
        AgentTask task = new AgentTask();
        task.setType(AgentTaskType.DIGEST);
        task.setClientId(clientId);
        task.setName(name);
        task.setPayload(serialize(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(computeNext("0 0 8 * * MON"));
        return toDto(agentTaskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<AgentTaskDto> findByClientId(UUID clientId) {
        return agentTaskRepository
            .findAllByClientIdAndStatusOrderByCreatedAtDesc(clientId, AgentTaskStatus.ACTIVE)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(UUID taskId) {
        agentTaskRepository.deleteById(taskId);
    }

    @Transactional
    public void markRan(AgentTask task) {
        task.setLastRunAt(Instant.now());
        if (task.getSchedule() != null) {
            task.setNextRunAt(computeNext(task.getSchedule()));
        } else {
            task.setStatus(AgentTaskStatus.COMPLETED);
        }
        agentTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<AgentTask> findDueTasks() {
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

    public AgentTaskDto toDto(AgentTask t) {
        return new AgentTaskDto(t.getId(), t.getType(), t.getStatus(), t.getClientId(),
            t.getName(), t.getSchedule(), t.getLastRunAt(), t.getNextRunAt(), t.getCreatedAt());
    }
}
```

- [ ] **Step 6: Run test — expect pass**

```
mvnw.cmd test -Dtest=AgentTaskServiceTest
```
Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 7: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/ hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/AgentTaskServiceTest.java
git commit -m "feat(agent): add AgentTaskService with watch/research/digest creation and scheduling"
```

---

## Task 4: AgentTaskHandler interface + WatchTaskHandler

**Files:**
- Create: `…/agent/internal/NotificationContent.java`
- Create: `…/agent/internal/AgentTaskHandler.java`
- Create: `…/agent/internal/WatchTaskHandler.java`
- Test: `…/agent/internal/WatchTaskHandlerTest.java`

**Interfaces:**
- Consumes: `AgentTask`, `AgentTaskType`, `ListingService.findForChat(...)`, `ListingDto`, `WatchPayload`
- Produces: `AgentTaskHandler` interface, `WatchTaskHandler` Spring `@Component`, `NotificationContent(String title, String body, List<UUID> listingIds)`

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/WatchTaskHandlerTest.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchTaskHandlerTest {

    @Mock ListingService listingService;
    @Spy ObjectMapper objectMapper;
    @InjectMocks WatchTaskHandler handler;

    @Test
    void returnsEmptyWhenNoNewListings() throws Exception {
        AgentTask task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto old = listing(Instant.now().minusSeconds(7200)); // seen before lastRunAt
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(old));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsNotificationWhenNewListingFound() throws Exception {
        AgentTask task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto newListing = listing(Instant.now().minusSeconds(60)); // seen after lastRunAt
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(newListing));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 new listing");
        assertThat(result.get().listingIds()).hasSize(1);
    }

    private AgentTask watchTask(Instant lastRunAt) throws Exception {
        WatchPayload payload = new WatchPayload("Utrecht", null, null, 400000, 3, null, null, null, null, null);
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.WATCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setClientId(UUID.randomUUID());
        task.setName("Utrecht 3-bed");
        task.setPayload(new ObjectMapper().writeValueAsString(payload));
        task.setLastRunAt(lastRunAt);
        task.setNextRunAt(Instant.now());
        return task;
    }

    private ListingDto listing(Instant firstSeenAt) {
        return new ListingDto(UUID.randomUUID(), "fundaId", "http://example.com",
            "Herenstraat", "10", null, "3500AA", "Utrecht", "Utrecht",
            null, null, null, 3, 5, 120, null, 350000, firstSeenAt,
            Instant.now(), com.kropholler.dev.hermes.listing.ListingStatus.FOR_SALE,
            null, null);
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=WatchTaskHandlerTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create NotificationContent**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/NotificationContent.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import java.util.List;
import java.util.UUID;

public record NotificationContent(String title, String body, List<UUID> listingIds) {}
```

- [ ] **Step 4: Create AgentTaskHandler interface**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/AgentTaskHandler.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskType;

import java.util.Optional;

public interface AgentTaskHandler {
    AgentTaskType getType();
    Optional<NotificationContent> handle(AgentTask task);
}
```

- [ ] **Step 5: Create WatchTaskHandler**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/WatchTaskHandler.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchTaskHandler implements AgentTaskHandler {

    private final ListingService listingService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentTaskType getType() { return AgentTaskType.WATCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        WatchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), WatchPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize WatchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        List<ListingDto> matches = listingService.findForChat(
            payload.minPrice(), payload.maxPrice(),
            payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
            payload.province(), payload.city(), payload.keywords(),
            false, null, payload.nearCity(), payload.radiusKm()
        );

        Instant since = task.getLastRunAt() != null ? task.getLastRunAt() : task.getCreatedAt();
        List<ListingDto> newListings = matches.stream()
            .filter(l -> l.firstSeenAt() != null && l.firstSeenAt().isAfter(since))
            .toList();

        if (newListings.isEmpty()) {
            log.info("Watch task {}: no new listings found", task.getId());
            return Optional.empty();
        }

        String title = newListings.size() + " new listing(s) match watch: " + task.getName();
        StringBuilder body = new StringBuilder();
        for (ListingDto l : newListings) {
            body.append("- ").append(l.street()).append(" ").append(l.houseNumber())
                .append(", ").append(l.city());
            if (l.currentPrice() != null)
                body.append(" — €").append(String.format("%,d", l.currentPrice()).replace(",", "."));
            body.append("\n");
        }

        List<UUID> ids = newListings.stream().map(ListingDto::id).toList();
        return Optional.of(new NotificationContent(title, body.toString(), ids));
    }
}
```

- [ ] **Step 6: Run test — expect pass**

```
mvnw.cmd test -Dtest=WatchTaskHandlerTest
```

Note: if `ListingDto` does not have `firstSeenAt()` as a record component, check `ListingDto.java` and adjust the field accessor accordingly.

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/ hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/WatchTaskHandlerTest.java
git commit -m "feat(agent): add AgentTaskHandler interface and WatchTaskHandler"
```

---

## Task 5: ResearchTaskHandler

**Files:**
- Create: `…/agent/internal/ResearchTaskHandler.java`
- Test: `…/agent/internal/ResearchTaskHandlerTest.java`

**Interfaces:**
- Consumes: `ChatClient` bean (qualifier `"chatClient"`), all 6 existing tool classes, `AgentTaskHandler`, `NotificationContent`, `ResearchPayload`
- Produces: `ResearchTaskHandler` Spring `@Component`

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/ResearchTaskHandlerTest.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.PromptSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock FavouriteService favouriteService;

    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    ObjectMapper objectMapper = new ObjectMapper();
    ResearchTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResearchTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favouriteService, meterRegistry, objectMapper);
    }

    @Test
    void returnsNotificationWithAiResponse() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Analysis complete: property A is the best deal.");

        AgentTask task = researchTask("analyse my favourites");

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().body()).contains("Analysis complete");
        assertThat(result.get().listingIds()).isEmpty();
    }

    @Test
    void returnsEmptyWhenAiReturnsBlank() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("  ");

        Optional<NotificationContent> result = handler.handle(researchTask("analyse"));

        assertThat(result).isEmpty();
    }

    private AgentTask researchTask(String prompt) throws Exception {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.RESEARCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setClientId(UUID.randomUUID());
        task.setName("Research: " + prompt);
        task.setPayload(objectMapper.writeValueAsString(new ResearchPayload(prompt)));
        task.setNextRunAt(Instant.now());
        return task;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=ResearchTaskHandlerTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create ResearchTaskHandler**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/ResearchTaskHandler.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.*;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class ResearchTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavouriteService favouriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public ResearchTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                                ListingService listingService,
                                ChatListingCardMapper chatListingCardMapper,
                                ListingSummaryService listingSummaryService,
                                FavouriteService favouriteService,
                                MeterRegistry meterRegistry,
                                ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.favouriteService = favouriteService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.RESEARCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        ResearchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), ResearchPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ResearchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID clientId = task.getClientId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(clientId, favouriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        String result = chatClient.prompt()
            .user(payload.prompt())
            .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favTool)
            .call()
            .content();

        if (result == null || result.isBlank()) return Optional.empty();

        return Optional.of(new NotificationContent(
            "Research complete: " + task.getName(), result, List.of()));
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```
mvnw.cmd test -Dtest=ResearchTaskHandlerTest
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/ResearchTaskHandler.java hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/ResearchTaskHandlerTest.java
git commit -m "feat(agent): add ResearchTaskHandler for background AI research tasks"
```

---

## Task 6: DigestTaskHandler

**Files:**
- Create: `…/agent/internal/DigestTaskHandler.java`
- Test: `…/agent/internal/DigestTaskHandlerTest.java`

**Interfaces:**
- Consumes: `ChatClient`, `ListingService`, all tool classes, `DigestPayload`, `AgentTaskHandler`
- Produces: `DigestTaskHandler` Spring `@Component`

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/DigestTaskHandlerTest.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigestTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.PromptSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock FavouriteService favouriteService;

    ObjectMapper objectMapper = new ObjectMapper();
    DigestTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DigestTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, favouriteService, new SimpleMeterRegistry(), objectMapper);
    }

    @Test
    void returnsDigestNotification() throws Exception {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("This week in Amsterdam: 3 new listings appeared...");

        AgentTask task = digestTask(List.of("Amsterdam", "Utrecht"));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("Weekly digest");
        assertThat(result.get().body()).contains("Amsterdam");
    }

    private AgentTask digestTask(List<String> cities) throws Exception {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.DIGEST);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setClientId(UUID.randomUUID());
        task.setName("Weekly digest");
        task.setPayload(objectMapper.writeValueAsString(new DigestPayload(cities)));
        task.setSchedule("0 0 8 * * MON");
        task.setNextRunAt(Instant.now());
        return task;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=DigestTaskHandlerTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create DigestTaskHandler**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/DigestTaskHandler.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.ai.*;
import com.kropholler.dev.hermes.favourites.FavouriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DigestTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final FavouriteService favouriteService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public DigestTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                              ListingService listingService,
                              ChatListingCardMapper chatListingCardMapper,
                              ListingSummaryService listingSummaryService,
                              FavouriteService favouriteService,
                              MeterRegistry meterRegistry,
                              ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.favouriteService = favouriteService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.DIGEST; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        DigestPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), DigestPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize DigestPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        String citiesList = payload.cities().stream().collect(Collectors.joining(", "));
        String prompt = """
            Generate a friendly weekly market digest for these cities: %s.
            For each city:
            1. Call searchListings to find current listings and summarise how many are available and typical price range.
            2. Call findPriceDrop to identify any notable price reductions.
            Write a brief, friendly summary paragraph per city. Keep it concise.
            """.formatted(citiesList);

        AtomicReference<List<ChatListingCard>> resultHolder = new AtomicReference<>(List.of());
        UUID clientId = task.getClientId();

        var searchTool    = new ListingSearchTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var summaryTool   = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool   = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool   = new CompareListingsTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var priceDropTool = new FindPriceDropTool(listingService, chatListingCardMapper, resultHolder, meterRegistry);
        var favTool       = new GetFavouriteListingsTool(clientId, favouriteService, listingService, chatListingCardMapper, resultHolder, meterRegistry);

        String result = chatClient.prompt()
            .user(prompt)
            .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favTool)
            .call()
            .content();

        if (result == null || result.isBlank()) return Optional.empty();

        return Optional.of(new NotificationContent(
            "Weekly digest: " + task.getName(), result, List.of()));
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```
mvnw.cmd test -Dtest=DigestTaskHandlerTest
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/DigestTaskHandler.java hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/DigestTaskHandlerTest.java
git commit -m "feat(agent): add DigestTaskHandler for AI-narrated weekly market digest"
```

---

## Task 7: NotificationService

**Files:**
- Create: `…/agent/NotificationService.java`
- Test: `…/agent/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationRepository`, `SimpMessagingTemplate`, `JavaMailSender`, `NotificationContent`, `Notification`, `NotificationDto`
- Produces: `NotificationService.save(UUID taskId, UUID clientId, NotificationContent) -> NotificationDto`, `findByClientId(UUID) -> List<NotificationDto>`, `countUnread(UUID) -> long`, `markRead(UUID)`

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/NotificationServiceTest.java`:
```java
package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.internal.Notification;
import com.kropholler.dev.hermes.agent.internal.NotificationContent;
import com.kropholler.dev.hermes.agent.internal.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock JavaMailSender mailSender;
    @Spy ObjectMapper objectMapper;
    @InjectMocks NotificationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fromEmail", "from@test.com");
        ReflectionTestUtils.setField(service, "toEmail", "to@test.com");
    }

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
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=NotificationServiceTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create NotificationService**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/NotificationService.java`:
```java
package com.kropholler.dev.hermes.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.internal.Notification;
import com.kropholler.dev.hermes.agent.internal.NotificationContent;
import com.kropholler.dev.hermes.agent.internal.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messaging;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Value("${hermes.notifications.to-email}")
    private String toEmail;

    @Transactional
    public NotificationDto save(UUID taskId, UUID clientId, NotificationContent content) {
        Notification notification = new Notification();
        notification.setTaskId(taskId);
        notification.setClientId(clientId);
        notification.setTitle(content.title());
        notification.setBody(content.body());
        notification.setListingIds(serializeIds(content.listingIds()));
        Notification saved = notificationRepository.save(notification);

        NotificationDto dto = toDto(saved, content.listingIds());
        messaging.convertAndSend("/topic/notifications/" + clientId, dto);
        sendEmailAsync(dto);
        return dto;
    }

    @Async
    protected void sendEmailAsync(NotificationDto dto) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("[Hermes] " + dto.title());
            msg.setText(dto.body());
            mailSender.send(msg);
            notificationRepository.findById(dto.id()).ifPresent(n -> {
                n.setEmailSentAt(Instant.now());
                notificationRepository.save(n);
            });
        } catch (Exception e) {
            log.error("Failed to send notification email for {}", dto.id(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findByClientId(UUID clientId) {
        return notificationRepository.findTop50ByClientIdOrderByCreatedAtDesc(clientId)
            .stream().map(n -> toDto(n, deserializeIds(n.getListingIds()))).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID clientId) {
        return notificationRepository.countByClientIdAndReadFalse(clientId);
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

    private NotificationDto toDto(Notification n, List<UUID> listingIds) {
        return new NotificationDto(n.getId(), n.getTaskId(), n.getClientId(),
            n.getTitle(), n.getBody(), listingIds, n.isRead(),
            n.getCreatedAt(), n.getEmailSentAt());
    }
}
```

- [ ] **Step 4: Fix the missing `@BeforeEach` import in test**

Add `import org.junit.jupiter.api.BeforeEach;` to `NotificationServiceTest.java`.

- [ ] **Step 5: Run test — expect pass**

```
mvnw.cmd test -Dtest=NotificationServiceTest
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/NotificationService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/NotificationServiceTest.java
git commit -m "feat(agent): add NotificationService with persist, WebSocket push, and async email"
```

---

## Task 8: AgentTaskExecutor + AgentTaskScheduler

**Files:**
- Create: `…/agent/internal/AgentTaskExecutor.java`
- Create: `…/agent/AgentTaskScheduler.java`
- Test: `…/agent/internal/AgentTaskExecutorTest.java`

**Interfaces:**
- Consumes: `List<AgentTaskHandler>`, `AgentTaskService`, `NotificationService`, `AgentTask`, `NotificationContent`
- Produces: `AgentTaskExecutor.execute(AgentTask)`, `AgentTaskScheduler` ticking every 60 s

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/AgentTaskExecutorTest.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.agent.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTaskExecutorTest {

    @Mock AgentTaskService agentTaskService;
    @Mock NotificationService notificationService;

    AgentTaskHandler watchHandler;
    AgentTaskExecutor executor;

    @BeforeEach
    void setUp() {
        watchHandler = mock(AgentTaskHandler.class);
        when(watchHandler.getType()).thenReturn(AgentTaskType.WATCH);
        executor = new AgentTaskExecutor(List.of(watchHandler), agentTaskService, notificationService);
    }

    @Test
    void callsHandlerAndSavesNotificationWhenContentPresent() {
        AgentTask task = task(AgentTaskType.WATCH);
        NotificationContent content = new NotificationContent("title", "body", List.of());
        when(watchHandler.handle(task)).thenReturn(Optional.of(content));

        executor.execute(task);

        verify(notificationService).save(task.getId(), task.getClientId(), content);
        verify(agentTaskService).markRan(task);
    }

    @Test
    void doesNotSaveNotificationWhenHandlerReturnsEmpty() {
        AgentTask task = task(AgentTaskType.WATCH);
        when(watchHandler.handle(task)).thenReturn(Optional.empty());

        executor.execute(task);

        verify(notificationService, never()).save(any(), any(), any());
        verify(agentTaskService).markRan(task);
    }

    @Test
    void logsWarningForUnknownTaskType() {
        AgentTask task = task(AgentTaskType.DIGEST); // no handler registered

        executor.execute(task); // must not throw

        verify(agentTaskService, never()).markRan(any());
    }

    private AgentTask task(AgentTaskType type) {
        AgentTask t = new AgentTask();
        t.setId(UUID.randomUUID());
        t.setType(type);
        t.setStatus(AgentTaskStatus.ACTIVE);
        t.setClientId(UUID.randomUUID());
        t.setName("test");
        t.setNextRunAt(Instant.now());
        return t;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=AgentTaskExecutorTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create AgentTaskExecutor**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/internal/AgentTaskExecutor.java`:
```java
package com.kropholler.dev.hermes.agent.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.agent.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentTaskExecutor {

    private final Map<AgentTaskType, AgentTaskHandler> handlers;
    private final AgentTaskService agentTaskService;
    private final NotificationService notificationService;

    public AgentTaskExecutor(List<AgentTaskHandler> handlerList,
                              AgentTaskService agentTaskService,
                              NotificationService notificationService) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(AgentTaskHandler::getType, h -> h));
        this.agentTaskService = agentTaskService;
        this.notificationService = notificationService;
    }

    public void execute(AgentTask task) {
        AgentTaskHandler handler = handlers.get(task.getType());
        if (handler == null) {
            log.warn("No handler registered for task type {}, skipping task {}", task.getType(), task.getId());
            return;
        }
        try {
            handler.handle(task).ifPresent(content ->
                notificationService.save(task.getId(), task.getClientId(), content));
            agentTaskService.markRan(task);
        } catch (Exception e) {
            log.error("Error executing task {}: {}", task.getId(), e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Create AgentTaskScheduler**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/AgentTaskScheduler.java`:
```java
package com.kropholler.dev.hermes.agent;

import com.kropholler.dev.hermes.agent.internal.AgentTask;
import com.kropholler.dev.hermes.agent.internal.AgentTaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskScheduler {

    private final AgentTaskService agentTaskService;
    private final AgentTaskExecutor executor;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<AgentTask> due = agentTaskService.findDueTasks();
        if (!due.isEmpty()) {
            log.info("AgentTaskScheduler: executing {} due task(s)", due.size());
            due.forEach(executor::execute);
        }
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```
mvnw.cmd test -Dtest=AgentTaskExecutorTest
```
Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 6: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/agent/ hermes-backend/src/test/java/com/kropholler/dev/hermes/agent/internal/AgentTaskExecutorTest.java
git commit -m "feat(agent): add AgentTaskExecutor strategy dispatch and AgentTaskScheduler"
```

---

## Task 9: OpenAPI additions + REST controllers

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Create: `…/api/AgentTaskController.java`
- Create: `…/api/NotificationController.java`

**Interfaces:**
- Consumes: `AgentTaskService`, `NotificationService`, `AgentTaskDto`, `NotificationDto`
- Produces: `GET /api/agent-tasks?clientId=`, `DELETE /api/agent-tasks/{id}`, `GET /api/notifications?clientId=`, `GET /api/notifications/unread-count?clientId=`, `PATCH /api/notifications/{id}/read`

- [ ] **Step 1: Add endpoints to api.yaml**

Open `hermes-backend/src/main/resources/openapi/api.yaml` and append these path entries in the `paths:` section. Also add the schemas in `components/schemas:`.

Add to `paths:`:
```yaml
  /api/agent-tasks:
    get:
      tags: [agent-tasks]
      operationId: getAgentTasks
      parameters:
        - name: clientId
          in: query
          required: true
          schema:
            type: string
            format: uuid
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
      tags: [agent-tasks]
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

  /api/notifications:
    get:
      tags: [notifications]
      operationId: getNotifications
      parameters:
        - name: clientId
          in: query
          required: true
          schema:
            type: string
            format: uuid
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
      tags: [notifications]
      operationId: getUnreadCount
      parameters:
        - name: clientId
          in: query
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Unread notification count
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UnreadCountResponse'

  /api/notifications/{id}/read:
    patch:
      tags: [notifications]
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
```

Add to `components/schemas:`:
```yaml
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
        clientId:
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

    NotificationResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        taskId:
          type: string
          format: uuid
        clientId:
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
```

- [ ] **Step 2: Create AgentTaskController**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/api/AgentTaskController.java`:
```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.agent.AgentTaskDto;
import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.api.generated.AgentTasksApi;
import com.kropholler.dev.hermes.api.generated.model.AgentTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class AgentTaskController implements AgentTasksApi {

    private final AgentTaskService agentTaskService;

    @Override
    public ResponseEntity<List<AgentTaskResponse>> getAgentTasks(UUID clientId) {
        List<AgentTaskResponse> responses = agentTaskService.findByClientId(clientId)
            .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AgentTaskResponse toResponse(AgentTaskDto dto) {
        AgentTaskResponse r = new AgentTaskResponse();
        r.setId(dto.id());
        r.setType(dto.type() != null ? dto.type().name() : null);
        r.setStatus(dto.status() != null ? dto.status().name() : null);
        r.setClientId(dto.clientId());
        r.setName(dto.name());
        r.setSchedule(dto.schedule());
        r.setLastRunAt(dto.lastRunAt() != null ? dto.lastRunAt().atOffset(java.time.ZoneOffset.UTC) : null);
        r.setNextRunAt(dto.nextRunAt() != null ? dto.nextRunAt().atOffset(java.time.ZoneOffset.UTC) : null);
        r.setCreatedAt(dto.createdAt() != null ? dto.createdAt().atOffset(java.time.ZoneOffset.UTC) : null);
        return r;
    }
}
```

- [ ] **Step 3: Create NotificationController**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/api/NotificationController.java`:
```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.agent.NotificationDto;
import com.kropholler.dev.hermes.agent.NotificationService;
import com.kropholler.dev.hermes.api.generated.NotificationsApi;
import com.kropholler.dev.hermes.api.generated.model.NotificationResponse;
import com.kropholler.dev.hermes.api.generated.model.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
class NotificationController implements NotificationsApi {

    private final NotificationService notificationService;

    @Override
    public ResponseEntity<List<NotificationResponse>> getNotifications(UUID clientId) {
        List<NotificationResponse> responses = notificationService.findByClientId(clientId)
            .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<UnreadCountResponse> getUnreadCount(UUID clientId) {
        UnreadCountResponse r = new UnreadCountResponse();
        r.setCount(notificationService.countUnread(clientId));
        return ResponseEntity.ok(r);
    }

    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(NotificationDto dto) {
        NotificationResponse r = new NotificationResponse();
        r.setId(dto.id());
        r.setTaskId(dto.taskId());
        r.setClientId(dto.clientId());
        r.setTitle(dto.title());
        r.setBody(dto.body());
        r.setListingIds(dto.listingIds() != null
            ? dto.listingIds().stream().map(UUID::toString).collect(Collectors.toList()) : List.of());
        r.setRead(dto.read());
        r.setCreatedAt(dto.createdAt() != null ? dto.createdAt().atOffset(ZoneOffset.UTC) : null);
        r.setEmailSentAt(dto.emailSentAt() != null ? dto.emailSentAt().atOffset(ZoneOffset.UTC) : null);
        return r;
    }
}
```

- [ ] **Step 4: Build to verify OpenAPI generation and compilation**

```
mvnw.cmd compile
```
Expected: BUILD SUCCESS. If the generator produces unexpected interface names, check `target/generated-sources/openapi/` and align the `implements` clause in the controllers.

- [ ] **Step 5: Commit**
```bash
git add hermes-backend/src/main/resources/openapi/api.yaml hermes-backend/src/main/java/com/kropholler/dev/hermes/api/
git commit -m "feat(agent): add OpenAPI endpoints and REST controllers for agent-tasks + notifications"
```

---

## Task 10: Chat tools — SaveWatchTool, TriggerResearchTool, ListWatchesTool

**Files:**
- Create: `…/ai/internal/SaveWatchTool.java`
- Create: `…/ai/internal/TriggerResearchTool.java`
- Create: `…/ai/internal/ListWatchesTool.java`
- Modify: `…/ai/AiChatService.java`
- Modify: `…/ai/AiConfig.java`
- Test: `…/ai/internal/SaveWatchToolTest.java`

**Interfaces:**
- Consumes: `AgentTaskService`, `WatchPayload`, `UUID clientId`
- Produces: Three tool classes instantiated fresh per request in `AiChatService.startStream`

- [ ] **Step 1: Write the test**

`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/internal/SaveWatchToolTest.java`:
```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.agent.AgentTaskDto;
import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.AgentTaskStatus;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.agent.internal.WatchPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveWatchToolTest {

    @Test
    void createsWatchWithExtractedCriteria() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.WATCH,
            AgentTaskStatus.ACTIVE, clientId, "Utrecht 3-bed", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createWatch(any(), anyString(), any())).thenReturn(dto);

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService);
        String result = tool.saveWatch("Utrecht 3-bed", "Utrecht", null, null, 400000, 3, null, null, null, null, null);

        ArgumentCaptor<WatchPayload> cap = ArgumentCaptor.forClass(WatchPayload.class);
        verify(agentTaskService).createWatch(eq(clientId), eq("Utrecht 3-bed"), cap.capture());
        assertThat(cap.getValue().city()).isEqualTo("Utrecht");
        assertThat(cap.getValue().maxPrice()).isEqualTo(400000);
        assertThat(result).contains("Utrecht 3-bed").contains("saved");
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
mvnw.cmd test -Dtest=SaveWatchToolTest -DfailIfNoTests=false
```

- [ ] **Step 3: Create SaveWatchTool**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/SaveWatchTool.java`:
```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import com.kropholler.dev.hermes.agent.internal.WatchPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@RequiredArgsConstructor
public class SaveWatchTool {

    private final UUID clientId;
    private final AgentTaskService agentTaskService;

    @Tool(description = "Save a listing watch that runs daily and sends a notification when new properties matching your criteria appear. "
        + "Call this when the user asks to be alerted, notified, or monitored for listings. "
        + "Use the same criteria you would pass to searchListings.")
    public String saveWatch(
        @ToolParam(required = false, description = "Friendly name for this watch, e.g. 'Utrecht 3-bed under 400k'") String name,
        @ToolParam(required = false, description = "City to filter by") String city,
        @ToolParam(required = false, description = "Province to filter by") String province,
        @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
        @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
        @ToolParam(required = false, description = "City to search near") String nearCity,
        @ToolParam(required = false, description = "Radius in km when nearCity is set") Integer radiusKm
    ) {
        String watchName = (name != null && !name.isBlank()) ? name : buildName(city, minBedrooms, maxPrice);
        WatchPayload payload = new WatchPayload(
            blankToNull(city), blankToNull(province), minPrice, maxPrice,
            minBedrooms, minRooms, minLivingAreaM2, blankToNull(keywords),
            blankToNull(nearCity), radiusKm
        );
        agentTaskService.createWatch(clientId, watchName, payload);
        return "Watch '" + watchName + "' saved — I'll alert you daily when matching listings appear.";
    }

    private static String buildName(String city, Integer minBedrooms, Integer maxPrice) {
        StringBuilder sb = new StringBuilder();
        if (city != null) sb.append(city).append(" ");
        if (minBedrooms != null) sb.append(minBedrooms).append("-bed ");
        if (maxPrice != null) sb.append("under €").append(String.format("%,d", maxPrice).replace(",", "."));
        return sb.toString().strip().isEmpty() ? "New watch" : sb.toString().strip();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
```

- [ ] **Step 4: Create TriggerResearchTool**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/TriggerResearchTool.java`:
```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.agent.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@RequiredArgsConstructor
public class TriggerResearchTool {

    private final UUID clientId;
    private final AgentTaskService agentTaskService;

    @Tool(description = "Queue a background research task. "
        + "Call this when the user wants a deep analysis, a full market report, or asks to 'research' something. "
        + "The AI will use all available tools to answer the prompt and deliver results as a notification. "
        + "Do NOT run research inline in chat — always use this tool.")
    public String triggerResearch(
        @ToolParam(description = "The research question or task to investigate in detail") String prompt
    ) {
        agentTaskService.createResearch(clientId, prompt);
        return "Research queued — results will appear as a notification shortly.";
    }
}
```

- [ ] **Step 5: Create ListWatchesTool**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListWatchesTool.java`:
```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.agent.AgentTaskDto;
import com.kropholler.dev.hermes.agent.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ListWatchesTool {

    private final UUID clientId;
    private final AgentTaskService agentTaskService;

    @Tool(description = "List the user's active watches (listing alerts). "
        + "Call this when the user asks what alerts or watches they have set up. "
        + "Optionally cancel a watch by providing its ID.")
    public String listWatches(
        @ToolParam(required = false, description = "ID of the watch to cancel. Omit to just list watches.") UUID cancelId
    ) {
        if (cancelId != null) {
            agentTaskService.delete(cancelId);
            return "Watch " + cancelId + " cancelled.";
        }

        List<AgentTaskDto> tasks = agentTaskService.findByClientId(clientId);
        if (tasks.isEmpty()) {
            return "You have no active watches. Ask me to set one up — for example: 'Alert me when a 3-bed house in Utrecht appears under €400,000.'";
        }

        StringBuilder sb = new StringBuilder("Your active watches:\n\n");
        for (AgentTaskDto t : tasks) {
            sb.append("- **").append(t.name()).append("** (ID: ").append(t.id()).append(")")
              .append(" — runs ").append(t.schedule() != null ? "daily at 08:00" : "once")
              .append(t.lastRunAt() != null ? ", last ran " + t.lastRunAt().toString().substring(0, 10) : "")
              .append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 6: Wire tools into AiChatService**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/AiChatService.java`, add `AgentTaskService` as a constructor parameter and create the three new tools in `startStream`:

Add field and constructor param:
```java
private final AgentTaskService agentTaskService;
```

In the constructor, add `AgentTaskService agentTaskService` parameter and `this.agentTaskService = agentTaskService;`.

In `startStream`, after the existing 6 tool instantiations, add:
```java
SaveWatchTool saveWatchTool = new SaveWatchTool(effectiveClientId, agentTaskService);
TriggerResearchTool researchTool = new TriggerResearchTool(effectiveClientId, agentTaskService);
ListWatchesTool listWatchesTool = new ListWatchesTool(effectiveClientId, agentTaskService);

log.info("startStream: registering 9 tools for session={}", sessionId);

Flux<String> tokens = chatClient.prompt()
    .messages(history)
    .user(userMessage)
    .tools(searchTool, summaryTool, historyTool, compareTool, priceDropTool, favouritesTool,
           saveWatchTool, researchTool, listWatchesTool)
    .stream()
    .content();
```

- [ ] **Step 7: Update system prompt in AiConfig**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/AiConfig.java`, append to `CHAT_SYSTEM_PROMPT`:
```java
static final String CHAT_SYSTEM_PROMPT = """
        You are a helpful real-estate assistant for the Hermes property tracker.
        Respond in the same language the user writes in.
        Keep replies concise and friendly.

        RULES:
        - Always call a tool before describing any property details. Never invent addresses, prices, bedrooms, or descriptions.
        - Call searchListings whenever the user asks about available properties or wants to find listings.
        - Call searchListings even if you searched with similar criteria earlier in the conversation.
        - Never mention Funda.nl or any external website. All data lives inside this application.
        - When passing parameters, omit a field rather than passing an empty string.
        - priceSort: use 'desc' for 'most expensive'/'luxury'/'highest price'; use 'asc' or omit for 'cheapest'/'lowest price' or no preference.
        - Only filter by city, province, or keywords when the user explicitly names them in their current message.
        - Call saveWatch when the user asks to be alerted, notified, or monitored for listings matching criteria.
        - Call triggerResearch when the user wants a deep analysis or report run in the background.
        - Call listWatches when the user asks what alerts or watches they have set up.
        - Never run research inline in the chat — always queue it via triggerResearch.
        """;
```

- [ ] **Step 8: Run test — expect pass**

```
mvnw.cmd test -Dtest=SaveWatchToolTest
```
Expected: BUILD SUCCESS.

- [ ] **Step 9: Run full backend test suite**

```
mvnw.cmd test
```
Expected: BUILD SUCCESS. Fix any failures before committing.

- [ ] **Step 10: Commit**
```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/internal/SaveWatchToolTest.java
git commit -m "feat(agent): add SaveWatchTool, TriggerResearchTool, ListWatchesTool and wire into chat"
```

---

## Task 11: Frontend — types, NotificationsService, notification bell

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`
- Create: `hermes-frontend/src/app/core/notifications.service.ts`
- Create: `hermes-frontend/src/app/shared/notification-bell.component.ts`
- Create: `hermes-frontend/src/app/shared/notification-bell.component.html`
- Modify: `hermes-frontend/src/app/app.component.ts`
- Modify: `hermes-frontend/src/app/app.component.html`

**Interfaces:**
- Produces: `NotificationsService` with `notifications` signal, `unreadCount` signal, `markRead(id)`, `delete(id)`; `NotificationBellComponent` selector `app-notification-bell`

- [ ] **Step 1: Add types to api.types.ts**

Append to `hermes-frontend/src/app/core/api.types.ts`:
```typescript
export interface AgentTaskResponse {
  id: string;
  type: string;
  status: string;
  clientId: string;
  name: string;
  schedule?: string;
  lastRunAt?: string;
  nextRunAt?: string;
  createdAt?: string;
}

export interface NotificationResponse {
  id: string;
  taskId?: string;
  clientId: string;
  title: string;
  body: string;
  listingIds: string[];
  read: boolean;
  createdAt: string;
  emailSentAt?: string;
}

export interface UnreadCountResponse {
  count: number;
}
```

- [ ] **Step 2: Create NotificationsService**

`hermes-frontend/src/app/core/notifications.service.ts`:
```typescript
import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { NotificationResponse, UnreadCountResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  private readonly clientId: string;
  private readonly stompClient: Client;
  private subscription?: StompSubscription;

  private readonly _notifications = signal<NotificationResponse[]>([]);
  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = computed(() => this._notifications().filter(n => !n.read).length);

  constructor() {
    this.clientId = localStorage.getItem('hermes-chat-session') ?? crypto.randomUUID();

    this.stompClient = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      reconnectDelay: 5000,
      onConnect: () => this.subscribeAndLoad(),
    });
    this.stompClient.activate();
  }

  private subscribeAndLoad(): void {
    this.loadNotifications();
    this.subscription?.unsubscribe();
    this.subscription = this.stompClient.subscribe(
      `/topic/notifications/${this.clientId}`,
      (msg: IMessage) => {
        const incoming = JSON.parse(msg.body) as NotificationResponse;
        this._notifications.update(prev => [incoming, ...prev]);
      }
    );
  }

  private loadNotifications(): void {
    this.http.get<NotificationResponse[]>(`/api/notifications?clientId=${this.clientId}`)
      .subscribe(items => this._notifications.set(items));
  }

  markRead(id: string): void {
    this.http.patch(`/api/notifications/${id}/read`, {}).subscribe(() => {
      this._notifications.update(prev =>
        prev.map(n => n.id === id ? { ...n, read: true } : n)
      );
    });
  }
}
```

- [ ] **Step 3: Create notification bell component**

`hermes-frontend/src/app/shared/notification-bell.component.ts`:
```typescript
import { Component, inject, signal } from '@angular/core';
import { NotificationsService } from '../core/notifications.service';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  protected readonly svc = inject(NotificationsService);
  protected panelOpen = signal(false);

  protected toggle(): void {
    this.panelOpen.update(o => !o);
  }

  protected markRead(id: string, event: Event): void {
    event.stopPropagation();
    this.svc.markRead(id);
  }
}
```

`hermes-frontend/src/app/shared/notification-bell.component.html`:
```html
<div class="relative">
  <button (click)="toggle()" class="relative p-2 text-gray-600 hover:text-gray-900">
    <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
        d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
    </svg>
    @if (svc.unreadCount() > 0) {
      <span class="absolute top-1 right-1 inline-flex items-center justify-center px-1.5 py-0.5 text-xs font-bold text-white bg-red-500 rounded-full">
        {{ svc.unreadCount() }}
      </span>
    }
  </button>

  @if (panelOpen()) {
    <div class="absolute right-0 mt-2 w-96 bg-white rounded-lg shadow-xl border border-gray-200 z-50 max-h-[500px] overflow-y-auto">
      <div class="p-3 border-b font-semibold text-gray-700">Notifications</div>
      @if (svc.notifications().length === 0) {
        <div class="p-4 text-sm text-gray-500">No notifications yet.</div>
      }
      @for (n of svc.notifications(); track n.id) {
        <div
          class="p-3 border-b hover:bg-gray-50 cursor-pointer"
          [class.bg-blue-50]="!n.read"
          (click)="markRead(n.id, $event)">
          <div class="font-medium text-sm text-gray-800">{{ n.title }}</div>
          <div class="text-xs text-gray-500 mt-1 line-clamp-2">{{ n.body }}</div>
          <div class="text-xs text-gray-400 mt-1">{{ n.createdAt | date:'short' }}</div>
        </div>
      }
    </div>
  }
</div>
```

- [ ] **Step 4: Add bell to AppComponent**

In `hermes-frontend/src/app/app.component.ts`, add `NotificationBellComponent` to imports:
```typescript
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ChatBubbleComponent } from './shared/chat-bubble.component';
import { NotificationBellComponent } from './shared/notification-bell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ChatBubbleComponent, NotificationBellComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {}
```

- [ ] **Step 5: Add bell to app.component.html**

Open `hermes-frontend/src/app/app.component.html`. Find the nav/header area and add `<app-notification-bell />` next to the existing navigation links. Exact location depends on current template — place it in the top-right of the header.

- [ ] **Step 6: Build frontend**

```
cd hermes-frontend && npm run build
```
Expected: Build successful, no TypeScript errors.

- [ ] **Step 7: Commit**
```bash
git add hermes-frontend/src/
git commit -m "feat(agent): add NotificationsService, WebSocket bell, and real-time notification panel"
```

---

## Task 12: Frontend — AgentTaskService + WatchesPage

**Files:**
- Create: `hermes-frontend/src/app/core/agent-task.service.ts`
- Create: `hermes-frontend/src/app/pages/watches/watches-page.component.ts`
- Create: `hermes-frontend/src/app/pages/watches/watches-page.component.html`
- Modify: `hermes-frontend/src/app/app.routes.ts`

**Interfaces:**
- Produces: `AgentTaskService.getTasks() -> Observable<AgentTaskResponse[]>`, `deleteTask(id)`, `WatchesPageComponent` at route `/watches`

- [ ] **Step 1: Create AgentTaskService**

`hermes-frontend/src/app/core/agent-task.service.ts`:
```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgentTaskResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class AgentTaskService {
  private readonly http = inject(HttpClient);
  private readonly clientId = localStorage.getItem('hermes-chat-session') ?? '';

  getTasks(): Observable<AgentTaskResponse[]> {
    return this.http.get<AgentTaskResponse[]>(`/api/agent-tasks?clientId=${this.clientId}`);
  }

  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`/api/agent-tasks/${id}`);
  }
}
```

- [ ] **Step 2: Create WatchesPageComponent**

`hermes-frontend/src/app/pages/watches/watches-page.component.ts`:
```typescript
import { Component, inject, signal, OnInit } from '@angular/core';
import { AgentTaskService } from '../../core/agent-task.service';
import { AgentTaskResponse } from '../../core/api.types';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-watches-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './watches-page.component.html',
})
export class WatchesPageComponent implements OnInit {
  private readonly svc = inject(AgentTaskService);
  protected tasks = signal<AgentTaskResponse[]>([]);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.svc.getTasks().subscribe(t => this.tasks.set(t));
  }

  protected delete(id: string): void {
    this.svc.deleteTask(id).subscribe(() =>
      this.tasks.update(prev => prev.filter(t => t.id !== id))
    );
  }
}
```

`hermes-frontend/src/app/pages/watches/watches-page.component.html`:
```html
<div class="p-6 max-w-4xl mx-auto">
  <h1 class="text-2xl font-bold text-gray-800 mb-6">Active Watches</h1>
  <p class="text-sm text-gray-500 mb-4">
    Watches are created through the AI chat. Ask something like
    <em>"Alert me when a 3-bed house in Utrecht appears under €400,000."</em>
  </p>

  @if (tasks().length === 0) {
    <div class="text-gray-500 text-sm bg-gray-50 rounded-lg p-6 text-center">
      No active watches. Open the chat and ask to be alerted when matching listings appear.
    </div>
  } @else {
    <div class="overflow-x-auto rounded-lg border border-gray-200">
      <table class="min-w-full divide-y divide-gray-200 text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-gray-600 font-medium">Name</th>
            <th class="px-4 py-3 text-left text-gray-600 font-medium">Type</th>
            <th class="px-4 py-3 text-left text-gray-600 font-medium">Schedule</th>
            <th class="px-4 py-3 text-left text-gray-600 font-medium">Last run</th>
            <th class="px-4 py-3 text-left text-gray-600 font-medium">Next run</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 bg-white">
          @for (task of tasks(); track task.id) {
            <tr class="hover:bg-gray-50">
              <td class="px-4 py-3 font-medium text-gray-800">{{ task.name }}</td>
              <td class="px-4 py-3 text-gray-600">{{ task.type }}</td>
              <td class="px-4 py-3 text-gray-500 text-xs">{{ task.schedule ?? 'one-shot' }}</td>
              <td class="px-4 py-3 text-gray-500">{{ task.lastRunAt ? (task.lastRunAt | date:'short') : '—' }}</td>
              <td class="px-4 py-3 text-gray-500">{{ task.nextRunAt | date:'short' }}</td>
              <td class="px-4 py-3 text-right">
                <button
                  (click)="delete(task.id)"
                  class="text-red-500 hover:text-red-700 text-xs font-medium">
                  Cancel
                </button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  }
</div>
```

- [ ] **Step 3: Add /watches route**

In `hermes-frontend/src/app/app.routes.ts`, add:
```typescript
{
  path: 'watches',
  loadComponent: () =>
    import('./pages/watches/watches-page.component').then(
      m => m.WatchesPageComponent
    ),
},
```

- [ ] **Step 4: Add Watches nav link**

In `hermes-frontend/src/app/app.component.html`, add a nav link to `/watches` alongside the existing navigation links.

- [ ] **Step 5: Build frontend**

```
cd hermes-frontend && npm run build
```
Expected: Build successful, no TypeScript errors.

- [ ] **Step 6: Commit**
```bash
git add hermes-frontend/src/
git commit -m "feat(agent): add AgentTaskService and WatchesPage for managing active watches"
```

# Admin "Run Now" for Agent Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin manually trigger immediate execution of one of their own active agent tasks (watch/digest/area-research), instead of waiting for its next scheduled run, for debugging.

**Architecture:** `AgentTaskService` gains a shared `findOwned(taskId, userId)` helper (404 if missing, `AccessDeniedException` if not owned by the caller), reused by both the existing `delete` and the new run-now path. `AgentTaskExecutor` gains an `@Async` `executeAsync` wrapper around its existing synchronous `execute`, so the scheduler's tick loop is untouched. A new `POST /api/agent-tasks/{id}/run` endpoint, gated with `@PreAuthorize("hasRole('ADMIN')")`, looks up the owned task and fires `executeAsync`, returning `202 Accepted` immediately (fire-and-forget — the result surfaces as a normal notification, exactly like a scheduled run). On the frontend, a shared `isAdminUser(keycloak)` helper (extracted from `AppComponent`, now needed in two places) gates a new "Run now" button on the existing Watches page.

**Tech Stack:** Spring Boot 4, Spring Security method security (`@PreAuthorize`), Spring `@Async`/`@EnableAsync`, OpenAPI Generator (spring interface-only), Angular 22, `keycloak-angular`/`keycloak-js`.

## Global Constraints

- Admin can only run-now their **own** tasks — this reuses the same ownership check as `delete`, it does not introduce an all-users task view (explicitly out of scope, per the existing 2026-07-03 role-gated-admin-features decision).
- A denied ownership check returns 403 (`AccessDeniedException`), not 404 — consistent with the existing `delete`/`markRead` precedent.
- The endpoint is fire-and-forget: it returns 202 as soon as the task is queued for async execution, it does not wait for the handler (which may call the LLM) to finish.
- `AgentTaskScheduler`'s existing synchronous `execute()` call in its tick loop must not change behavior.

---

## Task 1: `AgentTaskService.findOwned` shared ownership-check helper

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`

**Interfaces:**
- Produces: `AgentTaskEntity AgentTaskService.findOwned(UUID taskId, UUID userId)` — throws `org.springframework.web.server.ResponseStatusException` (404) if the task doesn't exist, `org.springframework.security.access.AccessDeniedException` if it exists but `userId` doesn't own it, otherwise returns the entity.

- [ ] **Step 1: Write the failing tests**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`, add these three tests directly after the existing `delete_throws403WhenNotOwnedByCaller` test (before `findDueTasks_delegatesToRepository`):

```java
    @Test
    void findOwned_ownerReturnsEntity() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(userId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        AgentTaskEntity result = service.findOwned(taskId, userId);

        assertThat(result).isSameAs(task);
    }

    @Test
    void findOwned_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.findById(taskId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findOwned(taskId, userId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Agent task " + taskId + " not found");
    }

    @Test
    void findOwned_throws403WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(ownerId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        assertThatThrownBy(() -> service.findOwned(taskId, callerId))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AgentTaskServiceTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `service.findOwned(...)` does not exist.

- [ ] **Step 3: Extract `findOwned` and refactor `delete` to use it**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`, replace:

```java
    @Transactional
    public void delete(UUID taskId, UUID userId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Agent task " + taskId + " not found"));
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to delete this agent task");
        }
        agentTaskRepository.delete(task);
        log.info("Deleted task {} for user {}", taskId, userId);
    }
```

with:

```java
    @Transactional
    public void delete(UUID taskId, UUID userId) {
        AgentTaskEntity task = findOwned(taskId, userId);
        agentTaskRepository.delete(task);
        log.info("Deleted task {} for user {}", taskId, userId);
    }

    @Transactional(readOnly = true)
    public AgentTaskEntity findOwned(UUID taskId, UUID userId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Agent task " + taskId + " not found"));
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to access this agent task");
        }
        return task;
    }
```

(No new imports needed — `HttpStatus`, `AccessDeniedException`, and `ResponseStatusException` are already imported in this file.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AgentTaskServiceTest -f hermes-backend/pom.xml`
Expected: PASS, 13 tests, 0 failures (10 existing + 3 new; the existing `delete_*` tests are unaffected since `delete`'s observable behavior is unchanged).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java
git commit -m "refactor(backend): extract AgentTaskService.findOwned for reuse by the run-now trigger"
```

---

## Task 2: `AgentTaskExecutor.executeAsync`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutor.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutorTest.java`

**Interfaces:**
- Consumes: nothing new (same `AgentTaskEntity`, `AgentTaskHandler`, `AgentTaskService`, `NotificationService` as today).
- Produces: `void AgentTaskExecutor.executeAsync(AgentTaskEntity task)` — `@Async`-annotated, delegates to the existing package-private `execute(task)`. `AsyncConfig` (already `@EnableAsync`-annotated, in `com.kropholler.dev.hermes.config`) makes this actually run on a background thread at runtime; unit tests call it directly (no Spring context), so it behaves synchronously in tests.

- [ ] **Step 1: Write the failing test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutorTest.java`, add this test directly after `callsHandlerAndSavesNotificationWhenContentPresent`:

```java
    @Test
    void executeAsync_delegatesToExecute() {
        AgentTaskEntity task = task(AgentTaskType.WATCH);
        NotificationContent content = new NotificationContent("title", "body", List.of());
        when(watchHandler.handle(task)).thenReturn(Optional.of(content));

        executor.executeAsync(task);

        verify(notificationService).save(task.getId(), task.getUserId(), content);
        verify(agentTaskService).markRan(task);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentTaskExecutorTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `executor.executeAsync(task)` does not exist.

- [ ] **Step 3: Add `executeAsync`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutor.java`, add the import `import org.springframework.scheduling.annotation.Async;` alongside the existing imports, and add this method directly after the closing brace of the existing `execute` method (before the class's closing brace):

```java
    @Async
    public void executeAsync(AgentTaskEntity task) {
        execute(task);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentTaskExecutorTest -f hermes-backend/pom.xml`
Expected: PASS, 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutor.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskExecutorTest.java
git commit -m "feat(backend): add AgentTaskExecutor.executeAsync for on-demand task triggering"
```

---

## Task 3: `POST /api/agent-tasks/{id}/run` endpoint

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/agent-tasks.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`

**Interfaces:**
- Consumes: `AgentTaskService.findOwned` (Task 1), `AgentTaskExecutor.executeAsync` (Task 2).
- Produces: `POST /api/agent-tasks/{id}/run` → 202 (empty body) on success, 404 if the task doesn't exist, 403 if it exists but isn't owned by the caller or the caller lacks the `admin` role. The OpenAPI Generator (bound to `mvn generate-sources`/`compile`/`test` via the `generate-agent-tasks` execution in `pom.xml`) regenerates `AgentTasksApi` with a new `default ResponseEntity<Void> runAgentTask(UUID id)` method from the yaml change in Step 1 — no manual codegen step needed.

- [ ] **Step 1: Add the new path to `agent-tasks.yaml`**

In `hermes-backend/src/main/resources/openapi/agent-tasks.yaml`, insert this block directly after the `/api/agent-tasks/{id}` path's `delete:` block (i.e. right before the `components:` line):

```yaml
  /api/agent-tasks/{id}/run:
    post:
      tags: [AgentTasks]
      operationId: runAgentTask
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '202':
          description: Task execution queued
        '404':
          description: Agent task not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '403':
          description: Agent task exists but does not belong to the caller, or caller is not an admin
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
```

- [ ] **Step 2: Run a build to regenerate the OpenAPI interface**

Run: `mvn generate-sources -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS. Confirm the new method exists:

Run: `grep -n "runAgentTask" hermes-backend/target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/ai/agent/task/openapi/AgentTasksApi.java`
Expected: a `default ResponseEntity<Void> runAgentTask(` line is present.

- [ ] **Step 3: Write the failing controller tests**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTaskController.class)
@Import(SecurityConfig.class)
class AgentTaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean AgentTaskService agentTaskService;
    @MockitoBean AgentTaskApiMapper agentTaskApiMapper;
    @MockitoBean AgentTaskExecutor agentTaskExecutor;

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
    void deleteAgentTask_usesSubjectFromJwt() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(eq(taskId), eq(callerId));
    }

    @Test
    void deleteAgentTask_ownershipDenied_returns403ProblemDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        doThrow(new AccessDeniedException("Not authorized to delete this agent task"))
            .when(agentTaskService).delete(any(), any());

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void runAgentTask_asAdmin_returns202AndTriggersAsyncExecution() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(taskId);
        task.setUserId(callerId);
        when(agentTaskService.findOwned(taskId, callerId)).thenReturn(task);

        mockMvc.perform(post("/api/agent-tasks/{id}/run", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted());

        verify(agentTaskExecutor).executeAsync(task);
    }

    @Test
    void runAgentTask_asAdmin_returns404WhenNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent task " + taskId + " not found"))
            .when(agentTaskService).findOwned(taskId, callerId);

        mockMvc.perform(post("/api/agent-tasks/{id}/run", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());

        verify(agentTaskExecutor, never()).executeAsync(any());
    }

    @Test
    void runAgentTask_asAdmin_ownershipDenied_returns403ProblemDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Not authorized to access this agent task"))
            .when(agentTaskService).findOwned(taskId, callerId);

        mockMvc.perform(post("/api/agent-tasks/{id}/run", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        verify(agentTaskExecutor, never()).executeAsync(any());
    }

    @Test
    void runAgentTask_asUser_returns403() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(post("/api/agent-tasks/{id}/run", taskId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());

        verify(agentTaskExecutor, never()).executeAsync(any());
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn test -Dtest=AgentTaskControllerTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error (`AgentTaskController` doesn't implement `runAgentTask`, and its constructor doesn't yet accept `AgentTaskExecutor`).

- [ ] **Step 5: Add `runAgentTask` to `AgentTaskController`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTasksApi;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgentTaskController implements AgentTasksApi {

    private final AgentTaskService agentTaskService;
    private final AgentTaskApiMapper agentTaskApiMapper;
    private final AgentTaskExecutor agentTaskExecutor;

    @Override
    public ResponseEntity<List<AgentTaskResponse>> getAgentTasks() {
        List<AgentTaskResponse> responses = agentTaskService.findByUserId(CurrentUser.current().id())
            .stream().map(agentTaskApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id, CurrentUser.current().id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> runAgentTask(UUID id) {
        AgentTaskEntity task = agentTaskService.findOwned(id, CurrentUser.current().id());
        agentTaskExecutor.executeAsync(task);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
```

(`@RequiredArgsConstructor` regenerates the constructor to take all three final fields — no manual constructor code needed.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=AgentTaskControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 8 tests, 0 failures.

- [ ] **Step 7: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/agent-tasks.yaml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java
git commit -m "feat(backend): add admin-only POST /api/agent-tasks/{id}/run endpoint"
```

---

## Task 4: Frontend — extract shared `isAdminUser` helper

**Files:**
- Create: `hermes-frontend/src/app/core/is-admin.ts`
- Test: `hermes-frontend/src/app/core/is-admin.spec.ts`
- Modify: `hermes-frontend/src/app/app.component.ts`

**Interfaces:**
- Produces: `isAdminUser(keycloak: Keycloak): boolean` — a pure function, same logic `AppComponent.isAdmin` already has, now needed by both `AppComponent` and (in Task 5) `WatchesPageComponent`.

- [ ] **Step 1: Write the failing test**

Create `hermes-frontend/src/app/core/is-admin.spec.ts`:

```ts
import Keycloak from 'keycloak-js';
import { isAdminUser } from './is-admin';

describe('isAdminUser', () => {
  it('returns false when tokenParsed is undefined', () => {
    const keycloak = { tokenParsed: undefined } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeFalse();
  });

  it('returns false when tokenParsed has no admin realm role', () => {
    const keycloak = { tokenParsed: { realm_access: { roles: ['user'] } } } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeFalse();
  });

  it('returns true when tokenParsed has the admin realm role', () => {
    const keycloak = { tokenParsed: { realm_access: { roles: ['admin'] } } } as unknown as Keycloak;
    expect(isAdminUser(keycloak)).toBeTrue();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `is-admin.ts` does not exist yet (compilation error for this spec file).

- [ ] **Step 3: Write `isAdminUser`**

Create `hermes-frontend/src/app/core/is-admin.ts`:

```ts
import Keycloak from 'keycloak-js';

export function isAdminUser(keycloak: Keycloak): boolean {
  const roles = keycloak.tokenParsed?.['realm_access'] as { roles?: string[] } | undefined;
  return roles?.roles?.includes('admin') ?? false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `isAdminUser`'s 3 tests pass.

- [ ] **Step 5: Use the helper in `AppComponent`**

In `hermes-frontend/src/app/app.component.ts`, add the import `import { isAdminUser } from './core/is-admin';`, and replace:

```ts
  get isAdmin(): boolean {
    const roles = this.keycloak.tokenParsed?.['realm_access'] as { roles?: string[] } | undefined;
    return roles?.roles?.includes('admin') ?? false;
  }
```

with:

```ts
  get isAdmin(): boolean {
    return isAdminUser(this.keycloak);
  }
```

(`AppComponent`'s own `isAdmin` getter and its existing tests in `app.component.spec.ts` are unchanged in behavior/interface, so that spec file needs no edits.)

- [ ] **Step 6: Run the frontend suite to confirm nothing else broke**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `AppComponent`'s existing `isAdmin` tests still pass, plus the 3 new `isAdminUser` tests (pre-existing unrelated chat-component failures, if any, are unchanged).

- [ ] **Step 7: Commit**

```bash
git add hermes-frontend/src/app/core/is-admin.ts \
        hermes-frontend/src/app/core/is-admin.spec.ts \
        hermes-frontend/src/app/app.component.ts
git commit -m "refactor(frontend): extract isAdminUser helper for reuse on the watches page"
```

---

## Task 5: Frontend — "Run now" button on the Watches page

**Files:**
- Modify: `hermes-frontend/src/app/core/agent-task.service.ts`
- Modify: `hermes-frontend/src/app/pages/watches/watches-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/watches/watches-page.component.html`
- Create: `hermes-frontend/src/app/pages/watches/watches-page.component.spec.ts`

**Interfaces:**
- Consumes: `isAdminUser` (Task 4).
- Produces: `AgentTaskService.runNow(id: string): Observable<void>`; `WatchesPageComponent.isAdmin: boolean` getter, `WatchesPageComponent.runNow(id: string): void`, `runningTaskId: Signal<string | null>`, `queuedTaskId: Signal<string | null>`, `runError: Signal<string | null>`.

- [ ] **Step 1: Write the failing test**

Create `hermes-frontend/src/app/pages/watches/watches-page.component.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { WatchesPageComponent } from './watches-page.component';
import { AgentTaskResponse } from '../../core/api.types';

describe('WatchesPageComponent', () => {
  let httpMock: HttpTestingController;
  let keycloakStub: { tokenParsed: Record<string, unknown> | undefined };

  const task: AgentTaskResponse = {
    id: 'task-1',
    type: 'WATCH',
    status: 'ACTIVE',
    userId: 'user-1',
    name: 'Utrecht 3-bed',
    schedule: '0 0 8 * * *',
    nextRunAt: '2026-07-09T08:00:00Z',
  };

  async function setup(roles: string[]) {
    keycloakStub = { tokenParsed: { realm_access: { roles } } };
    await TestBed.configureTestingModule({
      imports: [WatchesPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WatchesPageComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/agent-tasks').flush([task]);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('reports isAdmin true for the admin realm role', async () => {
    const fixture = await setup(['admin']);
    expect(fixture.componentInstance.isAdmin).toBeTrue();
  });

  it('reports isAdmin false for a non-admin role', async () => {
    const fixture = await setup(['user']);
    expect(fixture.componentInstance.isAdmin).toBeFalse();
  });

  it('runNow sets queuedTaskId on success', async () => {
    const fixture = await setup(['admin']);

    fixture.componentInstance.runNow('task-1');
    expect(fixture.componentInstance.runningTaskId()).toBe('task-1');

    const req = httpMock.expectOne('/api/agent-tasks/task-1/run');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 202, statusText: 'Accepted' });

    expect(fixture.componentInstance.runningTaskId()).toBeNull();
    expect(fixture.componentInstance.queuedTaskId()).toBe('task-1');
    expect(fixture.componentInstance.runError()).toBeNull();
  });

  it('runNow sets runError on failure', async () => {
    const fixture = await setup(['admin']);

    fixture.componentInstance.runNow('task-1');
    const req = httpMock.expectOne('/api/agent-tasks/task-1/run');
    req.flush({ detail: 'Not authorized to access this agent task' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.runningTaskId()).toBeNull();
    expect(fixture.componentInstance.queuedTaskId()).toBeNull();
    expect(fixture.componentInstance.runError()).toBe('Not authorized to access this agent task');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `WatchesPageComponent` has no `isAdmin`/`runNow`/`runningTaskId`/`queuedTaskId`/`runError` members (compilation error).

- [ ] **Step 3: Add `runNow` to `AgentTaskService`**

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

  runNow(id: string): Observable<void> {
    return this.http.post<void>(`/api/agent-tasks/${id}/run`, null);
  }
}
```

- [ ] **Step 4: Update `WatchesPageComponent`**

Replace the full contents of `hermes-frontend/src/app/pages/watches/watches-page.component.ts`:

```ts
import { Component, inject, signal, OnInit } from '@angular/core';
import { AgentTaskService } from '../../core/agent-task.service';
import { AgentTaskResponse } from '../../core/api.types';
import { DatePipe } from '@angular/common';
import Keycloak from 'keycloak-js';
import { isAdminUser } from '../../core/is-admin';

@Component({
  selector: 'app-watches-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './watches-page.component.html',
})
export class WatchesPageComponent implements OnInit {
  private readonly svc = inject(AgentTaskService);
  private readonly keycloak = inject(Keycloak);
  protected tasks = signal<AgentTaskResponse[]>([]);
  protected runningTaskId = signal<string | null>(null);
  protected queuedTaskId = signal<string | null>(null);
  protected runError = signal<string | null>(null);

  protected get isAdmin(): boolean {
    return isAdminUser(this.keycloak);
  }

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

  protected runNow(id: string): void {
    this.runningTaskId.set(id);
    this.queuedTaskId.set(null);
    this.runError.set(null);
    this.svc.runNow(id).subscribe({
      next: () => {
        this.runningTaskId.set(null);
        this.queuedTaskId.set(id);
      },
      error: err => {
        this.runningTaskId.set(null);
        this.runError.set(err.error?.detail ?? 'Failed to run task');
      },
    });
  }
}
```

- [ ] **Step 5: Add the "Run now" button to the template**

Replace the full contents of `hermes-frontend/src/app/pages/watches/watches-page.component.html`:

```html
<div class="p-6 max-w-4xl mx-auto">
  <h1 class="text-2xl font-bold text-gray-800 mb-6">Active Watches</h1>
  <p class="text-sm text-gray-500 mb-4">
    Watches are created through the AI chat. Ask something like
    <em>"Alert me when a 3-bed house in Utrecht appears under &euro;400,000."</em>
  </p>

  @if (runError()) {
    <div class="text-sm text-red-600 bg-red-50 rounded-lg p-3 mb-4">{{ runError() }}</div>
  }

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
              <td class="px-4 py-3 text-gray-500">{{ task.lastRunAt ? (task.lastRunAt | date:'short') : '&mdash;' }}</td>
              <td class="px-4 py-3 text-gray-500">{{ task.nextRunAt | date:'short' }}</td>
              <td class="px-4 py-3 text-right space-x-3 whitespace-nowrap">
                @if (isAdmin) {
                  @if (queuedTaskId() === task.id) {
                    <span class="text-emerald-600 text-xs font-medium">Queued</span>
                  } @else {
                    <button
                      (click)="runNow(task.id)"
                      [disabled]="runningTaskId() === task.id"
                      class="text-cyan-600 hover:text-cyan-800 text-xs font-medium disabled:opacity-50">
                      {{ runningTaskId() === task.id ? 'Running...' : 'Run now' }}
                    </button>
                  }
                }
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

- [ ] **Step 6: Run test to verify it passes**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `WatchesPageComponent`'s 4 new tests pass.

- [ ] **Step 7: Run the frontend build**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 8: Commit**

```bash
git add hermes-frontend/src/app/core/agent-task.service.ts \
        hermes-frontend/src/app/pages/watches/watches-page.component.ts \
        hermes-frontend/src/app/pages/watches/watches-page.component.html \
        hermes-frontend/src/app/pages/watches/watches-page.component.spec.ts
git commit -m "feat(frontend): add admin-only Run now button to the Watches page"
```

---

## Task 6: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Confirm the button is admin-only and works**

Log in as `testuser` / `password`. Open "Watches" (create one first via chat if empty, e.g. "save a watch for 3-bed houses in Utrecht under 400k"). Confirm no "Run now" button appears next to the watch — only "Cancel".

Log in as `testadmin` / `password`. Create (or reuse) a watch of your own via chat. Open "Watches" and confirm a "Run now" button appears. Click it — confirm it briefly shows "Running...", then "Queued", and within a minute or so a notification appears (bell icon) with the watch's results, and the watch's "Last run" timestamp updates on next page load.

- [ ] **Step 3: Confirm ownership and role enforcement via direct API calls**

As `testadmin`, note a watch id that belongs to `testuser` (from `GET /api/agent-tasks` while authenticated as `testuser`, or from the chat "what are my watches"). Attempt to run it as `testadmin`:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/agent-tasks/{taskId}/run \
  -H "Authorization: Bearer <testadmin-token>"
```

Expected: `403` (the task genuinely exists, `testadmin` just isn't its owner).

As `testuser`, attempt to run one of their own tasks:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/agent-tasks/{taskId}/run \
  -H "Authorization: Bearer <testuser-token>"
```

Expected: `403` (not an admin, `@PreAuthorize` denies before the ownership check even runs).

Finally, as `testadmin`, attempt to run a nonexistent task id:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/agent-tasks/00000000-0000-0000-0000-000000000000/run \
  -H "Authorization: Bearer <testadmin-token>"
```

Expected: `404`, confirming the two denial modes (403 not-owned vs. 404 not-found) are still distinguished.

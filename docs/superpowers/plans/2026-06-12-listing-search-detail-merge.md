# Listing Search, Detail Fix & Report Merge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-side paginated search to the listings page, fix the broken detail page (blank due to stale frontend types), and merge the report page into the detail page.

**Architecture:** Backend gains JPA `Specification`-based filtering on `GET /api/listings` with five optional query params. Frontend gets debounced search inputs and a merged detail+report view using `forkJoin`. The separate report route and component are deleted.

**Tech Stack:** Java 25 / Spring Boot 4, JPA `Specification`, OpenAPI code-gen (Maven plugin); Angular 22 signals, `forkJoin`, `HttpParams`, Chart.js / ng2-charts.

---

## File Map

**Create:**
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java`

**Modify:**
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`
- `hermes-backend/src/main/resources/openapi/api.yaml`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`
- `hermes-frontend/src/app/core/api.types.ts`
- `hermes-frontend/src/app/core/listings.service.ts`
- `hermes-frontend/src/app/pages/listings/listings-page.component.ts`
- `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`
- `hermes-frontend/src/app/app.routes.ts`

**Delete:**
- `hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts`

---

### Task 1: JPA search infrastructure

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java`

**Background:** `ListingRepository` currently only extends `JpaRepository`. Adding `JpaSpecificationExecutor<Listing>` gives it `findAll(Specification<Listing>, Pageable)`. The `ListingSpecifications` utility builds a case-insensitive `LIKE` predicate for each non-blank field. `ListingSearchParams` is a public record that carries the five optional filter values and knows if all are blank.

- [ ] **Step 1: Write the failing tests**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java`:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceSearchTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;

    @InjectMocks
    private ListingService service;

    @Test
    void findAll_withNonEmptyParams_usesSpecificationPath() {
        var params = new ListingSearchParams("Teststraat", null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(any(Specification.class), eq(pageable));
        verify(listingRepository, never()).findAll(eq(pageable));
    }

    @Test
    void findAll_withEmptyParams_usesSimplePath() {
        var params = new ListingSearchParams(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(eq(pageable));
        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listingSearchParams_isEmpty_trueWhenAllBlank() {
        var params = new ListingSearchParams("", " ", null, "", null);
        assert params.isEmpty();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenAnyNonBlank() {
        var params = new ListingSearchParams(null, null, null, "1234AB", null);
        assert !params.isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ListingServiceSearchTest -q
```

Expected: compilation failure — `ListingSearchParams` does not exist yet, `ListingService.findAll(ListingSearchParams, Pageable)` does not exist.

- [ ] **Step 3: Create `ListingSearchParams`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java`:

```java
package com.kropholler.dev.hermes.listing;

public record ListingSearchParams(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String province
) {
    public boolean isEmpty() {
        return isBlank(street) && isBlank(houseNumber) && isBlank(houseNumberAddition)
            && isBlank(zipCode) && isBlank(province);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 4: Create `ListingSpecifications`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java`:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import org.springframework.data.jpa.domain.Specification;

class ListingSpecifications {

    static Specification<Listing> withParams(ListingSearchParams params) {
        return Specification
            .where(likeIgnoreCase("street", params.street()))
            .and(likeIgnoreCase("houseNumber", params.houseNumber()))
            .and(likeIgnoreCase("houseNumberAddition", params.houseNumberAddition()))
            .and(likeIgnoreCase("zipCode", params.zipCode()))
            .and(likeIgnoreCase("province", params.province()));
    }

    private static Specification<Listing> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) return null;
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }
}
```

- [ ] **Step 5: Extend `ListingRepository` with `JpaSpecificationExecutor`**

Modify `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {
    Optional<Listing> findByFundaId(String fundaId);
    Page<Listing> findAllByDeletedAtIsNull(Pageable pageable);
    void deleteAllByDeletedAtIsNotNull();
}
```

- [ ] **Step 6: Add `findAll(ListingSearchParams, Pageable)` to `ListingService`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`, add the new overload after the existing `findAll(Pageable)` method:

```java
@Transactional(readOnly = true)
public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
    if (params.isEmpty()) {
        return listingRepository.findAll(pageable).map(this::toDto);
    }
    return listingRepository.findAll(ListingSpecifications.withParams(params), pageable)
        .map(this::toDto);
}
```

- [ ] **Step 7: Run tests to verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ListingServiceSearchTest -q
```

Expected: all 4 tests pass.

- [ ] **Step 8: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java
git commit -m "feat(hermes-backend): add JPA search infrastructure for listing search"
```

---

### Task 2: Wire search into OpenAPI spec and controller

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java`

**Background:** The backend uses OpenAPI code generation. The `ListingController` implements the generated `ListingsApi` interface. After updating `api.yaml` with the five new optional query params, Maven regenerates the interface — the `getListings` method signature gains five new `String` parameters. The controller must implement the new signature and pass the values to `ListingService.findAll(ListingSearchParams, Pageable)`.

- [ ] **Step 1: Write the failing controller test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java`:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.listing.ListingSearchParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.report.ReportService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListingController.class)
class ListingControllerSearchTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ListingService listingService;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ReportService reportService;
    @MockitoBean ListingSummaryService summaryService;

    @Test
    void getListings_passesStreetParamToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings").param("street", "Dorpstraat"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Dorpstraat");
    }

    @Test
    void getListings_withNoParams_passesEmptyParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().isEmpty()).isTrue();
    }

    @Test
    void getListings_passesAllSearchParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings")
                .param("street", "Kerkstraat")
                .param("houseNumber", "5")
                .param("zipCode", "1234AB")
                .param("province", "Noord-Holland"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Kerkstraat");
        assertThat(cap.getValue().houseNumber()).isEqualTo("5");
        assertThat(cap.getValue().zipCode()).isEqualTo("1234AB");
        assertThat(cap.getValue().province()).isEqualTo("Noord-Holland");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ListingControllerSearchTest -q
```

Expected: tests fail — the controller still has the old `getListings(Integer, Integer)` signature, does not accept the new params.

- [ ] **Step 3: Add search params to `api.yaml`**

In `hermes-backend/src/main/resources/openapi/api.yaml`, replace the `parameters` block of `GET /api/listings` (lines under `operationId: getListings`) with:

```yaml
  /api/listings:
    get:
      operationId: getListings
      tags: [Listings]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
        - name: street
          in: query
          schema:
            type: string
        - name: houseNumber
          in: query
          schema:
            type: string
        - name: houseNumberAddition
          in: query
          schema:
            type: string
        - name: zipCode
          in: query
          schema:
            type: string
        - name: province
          in: query
          schema:
            type: string
      responses:
        '200':
          description: Paginated listing results
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ListingPage'
```

- [ ] **Step 4: Regenerate the API interface**

```
cd hermes-backend
./mvnw generate-sources -q
```

Expected: no errors. The generated file `target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/api/generated/ListingsApi.java` now has `getListings` with 7 parameters.

- [ ] **Step 5: Update `ListingController.getListings` to implement the new signature**

Replace the `getListings` method in `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`:

```java
@Override
public ResponseEntity<ListingPage> getListings(Integer page, Integer size,
        String street, String houseNumber, String houseNumberAddition,
        String zipCode, String province) {
    ListingSearchParams params = new ListingSearchParams(street, houseNumber, houseNumberAddition, zipCode, province);
    Page<ListingDto> result = listingService.findAll(params, PageRequest.of(page, size));
    ListingPage response = new ListingPage()
        .content(result.getContent().stream().map(this::toSummaryResponse).toList())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages())
        .page(result.getNumber())
        .size(result.getSize());
    return ResponseEntity.ok(response);
}
```

Also add the import at the top of `ListingController.java`:

```java
import com.kropholler.dev.hermes.listing.ListingSearchParams;
```

- [ ] **Step 6: Run all backend tests**

```
cd hermes-backend
./mvnw test -q
```

Expected: all tests pass, including the new `ListingControllerSearchTest`.

- [ ] **Step 7: Commit**

```
git add hermes-backend/src/main/resources/openapi/api.yaml
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java
git commit -m "feat(hermes-backend): wire listing search into controller and OpenAPI spec"
```

---

### Task 3: Fix frontend API types

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`

**Background:** The frontend types are stale in three ways: (1) `ListingDetailResponse` has `latestSnapshot` which the backend removed — it now returns `currentPrice` and `status` directly; (2) `PricePointResponse` has `scrapedAt`/`askingPrice` but the backend sends `timestamp`/`price`; (3) `ListingReportResponse` has `statusHistory` and `daysListedOnFunda` which the backend no longer sends. Fix all three so the TypeScript compiler catches any stale template references immediately.

- [ ] **Step 1: Replace the content of `api.types.ts`**

Write the full corrected file at `hermes-frontend/src/app/core/api.types.ts`:

```typescript
export type SessionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
export type SessionType = 'SEARCH' | 'RESCRAPE';
export type ListingStatus = 'FOR_SALE' | 'UNDER_OFFER' | 'SOLD' | 'WITHDRAWN';

export interface ListingSearchFilter {
  street?: string;
  houseNumber?: string;
  houseNumberAddition?: string;
  zipCode?: string;
  province?: string;
}

export interface ScrapingSessionResponse {
  id: string;
  status: SessionStatus;
  type: SessionType;
  createdAt: string;
  completedAt?: string;
}

export interface CreateScrapingSessionRequest {
  city: string;
  minPrice?: number;
  maxPrice?: number;
  minArea?: number;
  maxArea?: number;
  pageLimit: number;
}

export interface ListingPage {
  content: ListingSummaryResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ListingSummaryResponse {
  id: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  askingPrice?: number;
  status?: ListingStatus;
  firstSeenAt: string;
}

export interface ListingDetailResponse {
  id: string;
  fundaId: string;
  url: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  firstSeenAt: string;
  lastSeenAt: string;
  currentPrice?: number;
  status?: ListingStatus;
}

export interface PricePointResponse {
  timestamp: string;
  price?: number;
}

export interface ListingReportResponse {
  listingId: string;
  daysInHermes: number;
  currentPrice?: number;
  initialPrice?: number;
  priceChangePct?: number;
  priceHistory: PricePointResponse[];
  currentStatus?: string;
}

export interface AiSummaryResponse {
  listingId: string;
  summary: string;
  generatedAt: string;
}

export interface ErrorResponse {
  error: string;
  detail: string;
}

export const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];
```

- [ ] **Step 2: Verify TypeScript compilation catches all stale usages**

```
cd hermes-frontend
npx tsc --noEmit
```

Expected: compilation errors pointing at `listing-report-page.component.ts` (uses `p.scrapedAt`, `p.askingPrice`, `report.statusHistory`, `report.daysListedOnFunda`) and `listing-detail-page.component.ts` (uses `listing.latestSnapshot`). These will be fixed in Task 5. The errors here are expected — they confirm the type fix is correct.

- [ ] **Step 3: Commit**

```
git add hermes-frontend/src/app/core/api.types.ts
git commit -m "fix(hermes-frontend): update API types to match current backend schema"
```

---

### Task 4: Search UI on listings page

**Files:**
- Modify: `hermes-frontend/src/app/core/listings.service.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`

**Background:** `ListingsService.loadListings` needs to accept an optional `ListingSearchFilter` and build the query string using Angular's `HttpParams`. The listings page gets five debounced input fields (street, house number, addition, zip code, province). A single `Subject<void>` debounced at 300ms triggers the search and resets to page 0 on every change. A clear button resets all inputs and reloads.

- [ ] **Step 1: Update `ListingsService.loadListings` to accept a filter**

Replace `loadListings` in `hermes-frontend/src/app/core/listings.service.ts`. Also add `HttpParams` to the import and `forkJoin` is not needed in this task. The full updated file:

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AiSummaryResponse,
  ListingDetailResponse,
  ListingPage,
  ListingReportResponse,
  ListingSearchFilter,
  ScrapingSessionResponse,
} from './api.types';

@Injectable({ providedIn: 'root' })
export class ListingsService {
  private readonly http = inject(HttpClient);

  readonly listings = signal<ListingPage>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 20,
  });
  readonly currentListing = signal<ListingDetailResponse | null>(null);
  readonly report = signal<ListingReportResponse | null>(null);
  readonly summary = signal<AiSummaryResponse | null>(null);
  readonly summaryNotFound = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadListings(page: number, size: number, filter?: ListingSearchFilter): void {
    this.loading.set(true);
    this.error.set(null);
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter?.street) params = params.set('street', filter.street);
    if (filter?.houseNumber) params = params.set('houseNumber', filter.houseNumber);
    if (filter?.houseNumberAddition) params = params.set('houseNumberAddition', filter.houseNumberAddition);
    if (filter?.zipCode) params = params.set('zipCode', filter.zipCode);
    if (filter?.province) params = params.set('province', filter.province);
    this.http.get<ListingPage>('/api/listings', { params }).subscribe({
      next: data => {
        this.listings.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to load listings');
        this.loading.set(false);
      },
    });
  }

  loadListing(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentListing.set(null);
    this.http.get<ListingDetailResponse>(`/api/listings/${id}`).subscribe({
      next: data => {
        this.currentListing.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.status === 404 ? '404' : (err.error?.detail ?? 'Failed to load listing'));
        this.loading.set(false);
      },
    });
  }

  loadReport(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.report.set(null);
    this.http.get<ListingReportResponse>(`/api/listings/${id}/report`).subscribe({
      next: data => {
        this.report.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.status === 404 ? '404' : (err.error?.detail ?? 'Failed to load report'));
        this.loading.set(false);
      },
    });
  }

  loadListingAndReport(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentListing.set(null);
    this.report.set(null);
    this.http.get<ListingDetailResponse>(`/api/listings/${id}`).subscribe({
      next: listing => {
        this.currentListing.set(listing);
        this.http.get<ListingReportResponse>(`/api/listings/${id}/report`).subscribe({
          next: report => {
            this.report.set(report);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: err => {
        this.error.set(err.status === 404 ? '404' : (err.error?.detail ?? 'Failed to load listing'));
        this.loading.set(false);
      },
    });
  }

  loadSummary(id: string): void {
    this.summary.set(null);
    this.summaryNotFound.set(false);
    this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`).subscribe({
      next: data => this.summary.set(data),
      error: () => this.summaryNotFound.set(true),
    });
  }

  rescrape(id: string): Observable<ScrapingSessionResponse> {
    return this.http.post<ScrapingSessionResponse>(`/api/listings/${id}/rescrape`, {});
  }
}
```

Note: `loadListingAndReport` loads the detail first, then the report sequentially. If the report fails (e.g., no price history yet), it still shows the listing — the report is treated as optional enhancement.

- [ ] **Step 2: Replace `listings-page.component.ts` with search inputs**

Write the full file at `hermes-frontend/src/app/pages/listings/listings-page.component.ts`:

```typescript
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { ListingsService } from '../../core/listings.service';
import { ListingSearchFilter } from '../../core/api.types';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EuroPricePipe } from '../../shared/euro-price.pipe';

@Component({
  selector: 'app-listings-page',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, EuroPricePipe],
  template: `
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-slate-900">Listings</h1>
      <p class="text-sm text-slate-500 mt-0.5">All tracked properties</p>
    </div>

    <!-- Search bar -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4 mb-4">
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-3">
        <input [(ngModel)]="street" (input)="onFilterChange()"
          placeholder="Street"
          class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
        <input [(ngModel)]="houseNumber" (input)="onFilterChange()"
          placeholder="House number"
          class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
        <input [(ngModel)]="houseNumberAddition" (input)="onFilterChange()"
          placeholder="Addition"
          class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
        <input [(ngModel)]="zipCode" (input)="onFilterChange()"
          placeholder="Zip code"
          class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
        <input [(ngModel)]="province" (input)="onFilterChange()"
          placeholder="Province"
          class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
      </div>
      <button (click)="clearFilters()"
        class="text-xs font-medium text-slate-400 hover:text-slate-600 transition-colors">
        Clear filters
      </button>
    </div>

    @if (svc.error()) {
      <div class="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
    }

    @if (svc.loading()) {
      <div class="flex items-center gap-2 text-sm text-slate-500">
        <span class="inline-block w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
        Loading listings...
      </div>
    } @else {
      <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead>
              <tr class="bg-slate-50">
                <th class="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Address</th>
                <th class="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider hidden sm:table-cell">City</th>
                <th class="px-4 py-3 text-right text-xs font-semibold text-slate-400 uppercase tracking-wider">Price</th>
                <th class="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Status</th>
                <th class="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider hidden md:table-cell">First Seen</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (listing of svc.listings().content; track listing.id) {
                <tr class="hover:bg-cyan-50 cursor-pointer transition-colors" (click)="navigate(listing.id)">
                  <td class="px-4 py-3.5 font-medium text-slate-900">
                    {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
                  </td>
                  <td class="px-4 py-3.5 text-slate-500 hidden sm:table-cell">{{ listing.city }}</td>
                  <td class="px-4 py-3.5 text-right font-medium text-slate-900 tabular-nums">{{ listing.askingPrice | euroPrice }}</td>
                  <td class="px-4 py-3.5">
                    @if (listing.status) {
                      <app-status-badge [status]="listing.status" />
                    } @else {
                      <span class="text-slate-300">—</span>
                    }
                  </td>
                  <td class="px-4 py-3.5 text-slate-500 hidden md:table-cell">{{ listing.firstSeenAt | date:'mediumDate' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="px-4 py-12 text-center">
                    <p class="text-slate-400 font-medium">No listings found</p>
                    <p class="text-xs text-slate-400 mt-1">Start a scraping session to populate listings</p>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="mt-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-sm text-slate-500">
          <span>Rows per page:</span>
          <select [(ngModel)]="pageSize" (ngModelChange)="onPageSizeChange()"
            class="rounded-lg border-slate-200 text-sm py-1 focus:border-cyan-500 focus:ring-cyan-500">
            <option [ngValue]="10">10</option>
            <option [ngValue]="20">20</option>
            <option [ngValue]="50">50</option>
          </select>
        </div>
        <div class="flex items-center gap-2 text-sm">
          <span class="text-slate-400">Page {{ currentPage + 1 }} of {{ svc.listings().totalPages || 1 }}</span>
          <button [disabled]="currentPage === 0" (click)="prev()"
            class="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-40 transition-colors">
            ←
          </button>
          <button [disabled]="currentPage >= svc.listings().totalPages - 1" (click)="next()"
            class="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-40 transition-colors">
            →
          </button>
        </div>
      </div>
    }
  `,
})
export class ListingsPageComponent implements OnInit, OnDestroy {
  protected readonly svc = inject(ListingsService);
  private readonly router = inject(Router);

  currentPage = 0;
  pageSize = 20;

  street = '';
  houseNumber = '';
  houseNumberAddition = '';
  zipCode = '';
  province = '';

  private filterChange$ = new Subject<void>();
  private filterSub?: Subscription;

  ngOnInit(): void {
    this.svc.loadListings(this.currentPage, this.pageSize);
    this.filterSub = this.filterChange$.pipe(debounceTime(300)).subscribe(() => {
      this.currentPage = 0;
      this.loadWithFilters();
    });
  }

  ngOnDestroy(): void {
    this.filterSub?.unsubscribe();
  }

  onFilterChange(): void {
    this.filterChange$.next();
  }

  clearFilters(): void {
    this.street = '';
    this.houseNumber = '';
    this.houseNumberAddition = '';
    this.zipCode = '';
    this.province = '';
    this.currentPage = 0;
    this.svc.loadListings(0, this.pageSize);
  }

  onPageSizeChange(): void {
    this.currentPage = 0;
    this.loadWithFilters();
  }

  prev(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadWithFilters();
    }
  }

  next(): void {
    this.currentPage++;
    this.loadWithFilters();
  }

  navigate(id: string): void {
    this.router.navigate(['/listings', id]);
  }

  private get currentFilter(): ListingSearchFilter {
    return {
      street: this.street || undefined,
      houseNumber: this.houseNumber || undefined,
      houseNumberAddition: this.houseNumberAddition || undefined,
      zipCode: this.zipCode || undefined,
      province: this.province || undefined,
    };
  }

  private loadWithFilters(): void {
    this.svc.loadListings(this.currentPage, this.pageSize, this.currentFilter);
  }
}
```

- [ ] **Step 3: Verify TypeScript compilation**

```
cd hermes-frontend
npx tsc --noEmit 2>&1 | grep -v "listing-report\|listing-detail"
```

Expected: no errors other than the still-stale `listing-report-page.component.ts` and `listing-detail-page.component.ts` (fixed in Task 5).

- [ ] **Step 4: Commit**

```
git add hermes-frontend/src/app/core/listings.service.ts
git add hermes-frontend/src/app/pages/listings/listings-page.component.ts
git commit -m "feat(hermes-frontend): add server-side search with debounced inputs on listings page"
```

---

### Task 5: Fix detail page, merge report, delete report route

**Files:**
- Modify: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`
- Modify: `hermes-frontend/src/app/app.routes.ts`
- Delete: `hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts`

**Background:** The detail page renders `listing.latestSnapshot` which is always undefined — the field was removed from the backend response. Replace the snapshot card with a stats row (Days in Hermes, price change %, current price, status) and a price history chart sourced from the report endpoint. The `loadListingAndReport` service method added in Task 4 loads both endpoints sequentially. The report page component and its route are removed entirely.

- [ ] **Step 1: Replace `listing-detail-page.component.ts`**

Write the full file at `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`:

```typescript
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ListingsService } from '../../core/listings.service';
import { ListingReportResponse, ScrapingSessionResponse, TERMINAL_STATUSES } from '../../core/api.types';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-listing-detail-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, BaseChartDirective, EuroPricePipe, StatusBadgeComponent],
  template: `
    @if (svc.error() === '404') {
      <div class="rounded-xl bg-white border border-slate-200 shadow-sm p-12 text-center">
        <p class="text-slate-500 font-medium">Listing not found</p>
        <a routerLink="/listings" class="mt-4 inline-block text-sm text-cyan-600 hover:text-cyan-500 font-medium">← Back to listings</a>
      </div>
    } @else {
      @if (svc.error()) {
        <div class="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
      }

      @if (svc.loading()) {
        <div class="flex items-center gap-2 text-sm text-slate-500">
          <span class="inline-block w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
          Loading...
        </div>
      }

      @if (svc.currentListing(); as listing) {
        <div class="mb-5">
          <a routerLink="/listings" class="text-sm text-cyan-600 hover:text-cyan-500 font-medium">← All listings</a>
        </div>

        <div class="mb-6">
          <h1 class="text-2xl font-bold text-slate-900 leading-tight">
            {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
          </h1>
          <p class="text-slate-500 mt-1">{{ listing.zipCode }} {{ listing.city }}, {{ listing.province }}</p>
        </div>

        <!-- Stats row -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Days in Hermes</p>
            <p class="text-3xl font-bold text-cyan-500 mt-2 tabular-nums">
              {{ svc.report()?.daysInHermes ?? '—' }}
            </p>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Price change</p>
            @if (svc.report()?.priceChangePct != null) {
              <p class="text-3xl font-bold mt-2 tabular-nums"
                [class]="svc.report()!.priceChangePct! <= 0 ? 'text-emerald-500' : 'text-red-500'">
                {{ svc.report()!.priceChangePct! | number:'1.1-1' }}%
              </p>
            } @else {
              <p class="text-3xl font-bold text-slate-300 mt-2">—</p>
            }
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Current price</p>
            <p class="text-3xl font-bold text-slate-900 mt-2 tabular-nums">{{ listing.currentPrice | euroPrice }}</p>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Status</p>
            @if (listing.status) {
              <div class="mt-2"><app-status-badge [status]="listing.status" /></div>
            } @else {
              <p class="text-slate-300 mt-2">—</p>
            }
          </div>
        </div>

        <!-- Price history chart -->
        @if (svc.report(); as report) {
          @if (report.priceHistory.length > 0) {
            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
              <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-5">Price history</h2>
              <canvas baseChart [data]="chartData()" [options]="chartOptions" type="line"></canvas>
            </div>
          }
        }

        <!-- Detail + AI summary -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Left: listing details -->
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3">
            <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Details</h2>
            <div class="grid grid-cols-2 gap-x-4 gap-y-2.5 text-sm">
              <span class="text-slate-400">First seen</span>
              <span class="font-medium text-slate-700">{{ listing.firstSeenAt | date:'mediumDate' }}</span>
              <span class="text-slate-400">Last seen</span>
              <span class="font-medium text-slate-700">{{ listing.lastSeenAt | date:'mediumDate' }}</span>
              <span class="text-slate-400">Funda ID</span>
              <span class="font-medium text-slate-700 tabular-nums">{{ listing.fundaId }}</span>
              <span class="text-slate-400">Listing URL</span>
              <a [href]="listing.url" target="_blank" rel="noopener"
                class="font-medium text-cyan-600 hover:text-cyan-500 truncate">Open on Funda</a>
            </div>
          </div>

          <!-- Right: AI summary + rescrape -->
          <div class="space-y-5">
            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-4">AI Summary</h2>
              @if (svc.summary(); as summary) {
                <p class="text-sm text-slate-700 leading-relaxed">{{ summary.summary }}</p>
                <p class="text-xs text-slate-400 mt-3">Generated {{ summary.generatedAt | date:'medium' }}</p>
              } @else if (svc.summaryNotFound()) {
                <p class="text-sm text-slate-400 italic">No summary available yet.</p>
              } @else {
                <div class="space-y-2.5">
                  <div class="h-3 bg-slate-100 rounded-full animate-pulse"></div>
                  <div class="h-3 bg-slate-100 rounded-full animate-pulse w-4/5"></div>
                  <div class="h-3 bg-slate-100 rounded-full animate-pulse w-3/5"></div>
                </div>
              }
            </div>

            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
              <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Rescrape</h2>
              <button (click)="triggerRescrape()" [disabled]="rescrapeLoading() || isRescrapePolling()"
                class="rounded-lg bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white
                       hover:bg-slate-700 disabled:opacity-50 transition-colors">
                @if (rescrapeLoading()) {
                  <span class="flex items-center gap-2">
                    <span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    Triggering...
                  </span>
                } @else if (isRescrapePolling()) {
                  <span class="flex items-center gap-2">
                    <span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    In progress...
                  </span>
                } @else {
                  Trigger rescrape
                }
              </button>
              @if (rescrapeSession(); as s) {
                <div class="flex items-center gap-2 text-sm text-slate-500">
                  <span>Session:</span>
                  <app-status-badge [status]="s.status" />
                </div>
              }
            </div>
          </div>
        </div>
      }
    }
  `,
})
export class ListingDetailPageComponent implements OnInit, OnDestroy {
  protected readonly svc = inject(ListingsService);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);

  protected readonly rescrapeSession = signal<ScrapingSessionResponse | null>(null);
  protected readonly rescrapeLoading = signal(false);
  private pollInterval?: ReturnType<typeof setInterval>;

  private get id(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  protected readonly isRescrapePolling = computed(() => {
    const s = this.rescrapeSession();
    return s !== null && !TERMINAL_STATUSES.includes(s.status);
  });

  protected readonly chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgb(15, 23, 42)',
        titleColor: 'rgb(148, 163, 184)',
        bodyColor: 'rgb(241, 245, 249)',
        padding: 10,
        cornerRadius: 8,
        callbacks: {
          label: ctx =>
            `€ ${ctx.parsed.y != null ? ctx.parsed.y.toLocaleString('nl-NL') : '—'}`,
        },
      },
    },
    scales: {
      x: {
        grid: { color: 'rgb(226, 232, 240)' },
        ticks: { color: 'rgb(100, 116, 139)', font: { size: 11 } },
      },
      y: {
        grid: { color: 'rgb(226, 232, 240)' },
        ticks: {
          color: 'rgb(100, 116, 139)',
          font: { size: 11 },
          callback: v => `€ ${Number(v).toLocaleString('nl-NL')}`,
        },
      },
    },
  };

  protected readonly chartData = computed<ChartData<'line'>>(() => {
    const report = this.svc.report();
    if (!report) return { labels: [], datasets: [] };
    return {
      labels: report.priceHistory.map(p =>
        new Date(p.timestamp).toLocaleDateString('nl-NL')
      ),
      datasets: [
        {
          label: 'Asking price',
          data: report.priceHistory.map(p => p.price ?? null),
          borderColor: 'rgb(6, 182, 212)',
          backgroundColor: 'rgba(6, 182, 212, 0.08)',
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          spanGaps: true,
          pointBackgroundColor: 'rgb(6, 182, 212)',
          pointRadius: 4,
          pointHoverRadius: 6,
        },
      ],
    };
  });

  ngOnInit(): void {
    this.svc.loadListingAndReport(this.id);
    this.svc.loadSummary(this.id);
  }

  ngOnDestroy(): void {
    this.clearPoll();
  }

  triggerRescrape(): void {
    this.rescrapeLoading.set(true);
    this.svc.rescrape(this.id).subscribe({
      next: session => {
        this.rescrapeSession.set(session);
        this.rescrapeLoading.set(false);
        this.startRescrapePoll(session.id);
      },
      error: () => this.rescrapeLoading.set(false),
    });
  }

  private startRescrapePoll(sessionId: string): void {
    this.clearPoll();
    this.pollInterval = setInterval(() => {
      this.http
        .get<ScrapingSessionResponse>(`/api/scraping-sessions/${sessionId}`)
        .subscribe({
          next: s => {
            this.rescrapeSession.set(s);
            if (TERMINAL_STATUSES.includes(s.status)) {
              this.clearPoll();
            }
          },
          error: () => this.clearPoll(),
        });
    }, 3000);
  }

  private clearPoll(): void {
    if (this.pollInterval !== undefined) {
      clearInterval(this.pollInterval);
      this.pollInterval = undefined;
    }
  }
}
```

- [ ] **Step 2: Remove the report route from `app.routes.ts`**

Replace the full content of `hermes-frontend/src/app/app.routes.ts`:

```typescript
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/listings', pathMatch: 'full' },
  {
    path: 'listings',
    loadComponent: () =>
      import('./pages/listings/listings-page.component').then(
        m => m.ListingsPageComponent
      ),
  },
  {
    path: 'listings/:id',
    loadComponent: () =>
      import('./pages/listing-detail/listing-detail-page.component').then(
        m => m.ListingDetailPageComponent
      ),
  },
  {
    path: 'scraping',
    loadComponent: () =>
      import('./pages/scraping/scraping-page.component').then(
        m => m.ScrapingPageComponent
      ),
  },
];
```

- [ ] **Step 3: Delete the report page component file**

```
rm hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts
```

- [ ] **Step 4: Verify TypeScript compilation is clean**

```
cd hermes-frontend
npx tsc --noEmit
```

Expected: no errors. All stale references to `latestSnapshot`, `scrapedAt`, `askingPrice`, `statusHistory`, and `daysListedOnFunda` are now gone.

- [ ] **Step 5: Run the frontend build to confirm no bundler errors**

```
cd hermes-frontend
npx ng build --configuration development 2>&1 | tail -20
```

Expected: build succeeds, no errors.

- [ ] **Step 6: Commit**

```
git add hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts
git add hermes-frontend/src/app/app.routes.ts
git rm hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts
git commit -m "feat(hermes-frontend): merge report into detail page, remove report route"
```

---

### Task 6: Full backend test run

- [ ] **Step 1: Run all backend tests**

```
cd hermes-backend
./mvnw test -q
```

Expected: all tests pass (green).

- [ ] **Step 2: If compilation fails due to generated source mismatch**

If the build fails because `ListingController` does not match the generated `ListingsApi` interface (possible if generate-sources was not triggered), run:

```
cd hermes-backend
./mvnw generate-sources compile test -q
```

Expected: all tests pass.

- [ ] **Step 3: Commit if any fixes were needed**

Only commit if Step 2 required changes. Otherwise no commit needed here.

---

### Task 7: Final verification

- [ ] **Step 1: Run full backend test suite one more time**

```
cd hermes-backend
./mvnw test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend type check**

```
cd hermes-frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Verify git log looks sane**

```
git log --oneline -6
```

Expected output (newest first):
```
feat(hermes-frontend): merge report into detail page, remove report route
feat(hermes-frontend): add server-side search with debounced inputs on listings page
fix(hermes-frontend): update API types to match current backend schema
feat(hermes-backend): wire listing search into controller and OpenAPI spec
feat(hermes-backend): add JPA search infrastructure for listing search
docs: add listing search, detail fix and report merge design spec
```

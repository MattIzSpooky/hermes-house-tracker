# Hermes Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Upgrade Angular 17 scaffold to Angular 22 (remove SSR, remove zone.js) | ✅ Done | 9c240a0 |
| Task 2: Configure Tailwind CSS 4 | ✅ Done | 9975001 |
| Task 3: Dev proxy + API types | ✅ Done | 2e8b88a |
| Task 4: Signal-based services | ✅ Done | dbeeec1 |
| Task 5: Shared components — EuroPricePipe and StatusBadgeComponent | ✅ Done | c8219e3 |
| Task 6: App shell — navigation and routes | ✅ Done | 95c7284 |
| Task 7: Scraping page | ✅ Done | 32f4d26 |
| Task 8: Listings page | ✅ Done | 3142500 |
| Task 9: Listing detail page | ✅ Done | da65188 |
| Task 10: Listing report page with price history chart | ✅ Done | d85c8cd |

**Goal:** Build an Angular 22 SPA with four pages (listings browser, listing detail, listing report, scraping sessions) that communicate with the existing Spring Boot backend at `http://localhost:8080`.

**Architecture:** Standalone components with signal-based services (`ListingsService`, `ScrapingService`) providing reactive state. The Angular dev server proxies `/api/**` to the backend. No SSR — the current scaffold's SSR setup must be removed.

**Tech Stack:** Angular 22 (zoneless, standalone), Tailwind CSS 4, Chart.js 4 + ng2-charts 6, Karma/Jasmine for unit tests.

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Rewrite | `hermes-frontend/package.json` | Angular 22 + new deps |
| Modify | `hermes-frontend/angular.json` | Remove zone.js + SSR; add proxyConfig; switch to `@angular/build` |
| Modify | `hermes-frontend/tsconfig.app.json` | Remove server entry points |
| Modify | `hermes-frontend/src/app/app.config.ts` | Zoneless + HttpClient |
| Modify | `hermes-frontend/src/app/app.routes.ts` | Four routes |
| Modify | `hermes-frontend/src/app/app.component.ts` | Nav shell |
| Rewrite | `hermes-frontend/src/app/app.component.html` | Nav + router-outlet |
| Rewrite | `hermes-frontend/src/app/app.component.spec.ts` | Updated for zoneless |
| Modify | `hermes-frontend/src/styles.scss` | Add Tailwind import |
| Create | `hermes-frontend/postcss.config.mjs` | Tailwind PostCSS plugin |
| Create | `hermes-frontend/proxy.conf.json` | Dev proxy to Spring Boot |
| Create | `hermes-frontend/src/app/core/api.types.ts` | Backend DTO interfaces |
| Create | `hermes-frontend/src/app/core/listings.service.ts` | Signal service for listings |
| Create | `hermes-frontend/src/app/core/scraping.service.ts` | Signal service for scraping |
| Create | `hermes-frontend/src/app/shared/euro-price.pipe.ts` | Dutch price formatter |
| Create | `hermes-frontend/src/app/shared/euro-price.pipe.spec.ts` | Pipe tests |
| Create | `hermes-frontend/src/app/shared/status-badge.component.ts` | Coloured status pill |
| Create | `hermes-frontend/src/app/shared/status-badge.component.spec.ts` | Badge tests |
| Create | `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts` | Scraping form + live status |
| Create | `hermes-frontend/src/app/pages/listings/listings-page.component.ts` | Paginated table |
| Create | `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts` | Detail + AI summary + rescrape |
| Create | `hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts` | Chart + stats + status history |
| Delete | `hermes-frontend/server.ts` | SSR Express server (unused) |
| Delete | `hermes-frontend/src/main.server.ts` | SSR bootstrap (unused) |
| Delete | `hermes-frontend/src/app/app.config.server.ts` | SSR config (unused) |

---

### Task 1: Upgrade Angular 17 scaffold to Angular 22 (remove SSR, remove zone.js)

**Files:**
- Rewrite: `hermes-frontend/package.json`
- Modify: `hermes-frontend/angular.json`
- Modify: `hermes-frontend/tsconfig.app.json`
- Modify: `hermes-frontend/src/app/app.config.ts`
- Rewrite: `hermes-frontend/src/app/app.component.spec.ts`
- Delete: `hermes-frontend/server.ts`, `hermes-frontend/src/main.server.ts`, `hermes-frontend/src/app/app.config.server.ts`

- [ ] **Step 1: Rewrite `package.json`**

Replace the full file with Angular 22 packages, switching `@angular-devkit/build-angular` to `@angular/build`, removing SSR and zone.js:

```json
{
  "name": "hermes-frontend",
  "version": "0.0.0",
  "scripts": {
    "ng": "ng",
    "start": "ng serve",
    "build": "ng build",
    "watch": "ng build --watch --configuration development",
    "test": "ng test"
  },
  "private": true,
  "dependencies": {
    "@angular/animations": "^22.0.0",
    "@angular/common": "^22.0.0",
    "@angular/compiler": "^22.0.0",
    "@angular/core": "^22.0.0",
    "@angular/forms": "^22.0.0",
    "@angular/platform-browser": "^22.0.0",
    "@angular/platform-browser-dynamic": "^22.0.0",
    "@angular/router": "^22.0.0",
    "rxjs": "~7.8.0",
    "tslib": "^2.3.0"
  },
  "devDependencies": {
    "@angular/build": "^22.0.0",
    "@angular/cli": "^22.0.0",
    "@angular/compiler-cli": "^22.0.0",
    "@types/jasmine": "~5.1.0",
    "@types/node": "^18.18.0",
    "jasmine-core": "~5.1.0",
    "karma": "~6.4.0",
    "karma-chrome-launcher": "~3.2.0",
    "karma-coverage": "~2.2.0",
    "karma-jasmine": "~5.1.0",
    "karma-jasmine-html-reporter": "~2.1.0",
    "typescript": "~5.8.0"
  }
}
```

- [ ] **Step 2: Delete SSR files**

Delete these three files (they are not used in a CSR-only Angular 22 app):
- `hermes-frontend/server.ts`
- `hermes-frontend/src/main.server.ts`
- `hermes-frontend/src/app/app.config.server.ts`

- [ ] **Step 3: Update `tsconfig.app.json` — remove server entry points**

Replace the full file:

```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "outDir": "./out-tsc/app",
    "types": []
  },
  "files": [
    "src/main.ts"
  ],
  "include": [
    "src/**/*.d.ts"
  ]
}
```

- [ ] **Step 4: Rewrite `angular.json`**

Replace the full file — switches builders to `@angular/build`, removes `zone.js` polyfills, removes SSR/prerender, adds `proxyConfig`:

```json
{
  "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
  "version": 1,
  "newProjectRoot": "projects",
  "projects": {
    "hermes-frontend": {
      "projectType": "application",
      "schematics": {
        "@schematics/angular:component": {
          "style": "scss"
        }
      },
      "root": "",
      "sourceRoot": "src",
      "prefix": "app",
      "architect": {
        "build": {
          "builder": "@angular/build:application",
          "options": {
            "outputPath": "dist/hermes-frontend",
            "index": "src/index.html",
            "browser": "src/main.ts",
            "polyfills": [],
            "tsConfig": "tsconfig.app.json",
            "inlineStyleLanguage": "scss",
            "assets": [
              "src/favicon.ico",
              "src/assets"
            ],
            "styles": [
              "src/styles.scss"
            ],
            "scripts": []
          },
          "configurations": {
            "production": {
              "budgets": [
                {
                  "type": "initial",
                  "maximumWarning": "500kb",
                  "maximumError": "1mb"
                },
                {
                  "type": "anyComponentStyle",
                  "maximumWarning": "2kb",
                  "maximumError": "4kb"
                }
              ],
              "outputHashing": "all"
            },
            "development": {
              "optimization": false,
              "extractLicenses": false,
              "sourceMap": true
            }
          },
          "defaultConfiguration": "production"
        },
        "serve": {
          "builder": "@angular/build:dev-server",
          "options": {
            "proxyConfig": "proxy.conf.json"
          },
          "configurations": {
            "production": {
              "buildTarget": "hermes-frontend:build:production"
            },
            "development": {
              "buildTarget": "hermes-frontend:build:development"
            }
          },
          "defaultConfiguration": "development"
        },
        "extract-i18n": {
          "builder": "@angular/build:extract-i18n",
          "options": {
            "buildTarget": "hermes-frontend:build"
          }
        },
        "test": {
          "builder": "@angular/build:karma",
          "options": {
            "polyfills": [],
            "tsConfig": "tsconfig.spec.json",
            "inlineStyleLanguage": "scss",
            "assets": [
              "src/favicon.ico",
              "src/assets"
            ],
            "styles": [
              "src/styles.scss"
            ],
            "scripts": []
          }
        }
      }
    }
  }
}
```

- [ ] **Step 5: Update `app.config.ts` — zoneless + HttpClient**

```typescript
import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withFetch()),
  ]
};
```

- [ ] **Step 6: Rewrite `app.component.spec.ts` — remove stale title tests**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
```

- [ ] **Step 7: Run `npm install` inside `hermes-frontend/`**

```bash
cd hermes-frontend && npm install
```

Expected: packages install without errors (may have peer dependency warnings — those are acceptable).

- [ ] **Step 8: Verify the build compiles**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds. The output will warn about unused imports in `app.component.html` — that's fine, we fix it in Task 6.

- [ ] **Step 9: Commit**

```bash
git add hermes-frontend/package.json hermes-frontend/package-lock.json hermes-frontend/angular.json hermes-frontend/tsconfig.app.json hermes-frontend/src/app/app.config.ts hermes-frontend/src/app/app.component.spec.ts
git commit -m "chore(frontend): upgrade to Angular 22, remove SSR and zone.js"
```

---

### Task 2: Configure Tailwind CSS 4

**Files:**
- Create: `hermes-frontend/postcss.config.mjs`
- Modify: `hermes-frontend/src/styles.scss`

- [ ] **Step 1: Install Tailwind 4 packages**

```bash
cd hermes-frontend && npm install tailwindcss @tailwindcss/postcss @tailwindcss/forms
```

Expected: installs without errors.

- [ ] **Step 2: Create `postcss.config.mjs`**

```js
export default {
  plugins: {
    '@tailwindcss/postcss': {}
  }
};
```

- [ ] **Step 3: Update `src/styles.scss`**

Replace the file contents:

```scss
@import "tailwindcss";
@plugin "@tailwindcss/forms";
```

- [ ] **Step 4: Verify the build still compiles**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds, Tailwind utility classes are now available.

- [ ] **Step 5: Commit**

```bash
git add hermes-frontend/postcss.config.mjs hermes-frontend/src/styles.scss hermes-frontend/package.json hermes-frontend/package-lock.json
git commit -m "feat(frontend): configure Tailwind CSS 4"
```

---

### Task 3: Dev proxy + API types

**Files:**
- Create: `hermes-frontend/proxy.conf.json`
- Create: `hermes-frontend/src/app/core/api.types.ts`

- [ ] **Step 1: Create `proxy.conf.json`**

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

- [ ] **Step 2: Create `src/app/core/api.types.ts`**

```typescript
export type SessionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
export type SessionType = 'CITY_SCRAPE' | 'RESCRAPE';

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
  status?: string;
  firstSeenAt: string;
}

export interface SnapshotResponse {
  id: string;
  scrapedAt: string;
  askingPrice?: number;
  livingAreaM2?: number;
  rooms?: number;
  energyLabel?: string;
  listedOnFundaSince?: string;
  status?: string;
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
  latestSnapshot?: SnapshotResponse;
}

export interface PricePointResponse {
  scrapedAt: string;
  askingPrice?: number;
}

export interface StatusPointResponse {
  scrapedAt: string;
  status: string;
}

export interface ListingReportResponse {
  listingId: string;
  daysListedOnFunda?: number;
  daysInHermes: number;
  currentPrice?: number;
  initialPrice?: number;
  priceChangePct?: number;
  priceHistory: PricePointResponse[];
  statusHistory: StatusPointResponse[];
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
```

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/proxy.conf.json hermes-frontend/src/app/core/api.types.ts
git commit -m "feat(frontend): add dev proxy config and API type definitions"
```

---

### Task 4: Signal-based services

**Files:**
- Create: `hermes-frontend/src/app/core/listings.service.ts`
- Create: `hermes-frontend/src/app/core/scraping.service.ts`

- [ ] **Step 1: Create `src/app/core/listings.service.ts`**

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AiSummaryResponse,
  ListingDetailResponse,
  ListingPage,
  ListingReportResponse,
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
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadListings(page: number, size: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<ListingPage>(`/api/listings?page=${page}&size=${size}`)
      .subscribe({
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
        this.error.set(err.error?.detail ?? 'Failed to load listing');
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
        this.error.set(err.error?.detail ?? 'Failed to load report');
        this.loading.set(false);
      },
    });
  }

  loadSummary(id: string): void {
    this.summary.set(null);
    this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`).subscribe({
      next: data => this.summary.set(data),
      error: () => this.summary.set(null),
    });
  }

  rescrape(id: string): Observable<ScrapingSessionResponse> {
    return this.http.post<ScrapingSessionResponse>(
      `/api/listings/${id}/rescrape`,
      {}
    );
  }
}
```

- [ ] **Step 2: Create `src/app/core/scraping.service.ts`**

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  CreateScrapingSessionRequest,
  ScrapingSessionResponse,
  SessionStatus,
} from './api.types';

const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

@Injectable({ providedIn: 'root' })
export class ScrapingService {
  private readonly http = inject(HttpClient);
  private pollInterval?: ReturnType<typeof setInterval>;

  readonly session = signal<ScrapingSessionResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  createSession(req: CreateScrapingSessionRequest): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .post<ScrapingSessionResponse>('/api/scraping-sessions', req)
      .subscribe({
        next: data => {
          this.session.set(data);
          this.loading.set(false);
          this.startPolling(data.id);
        },
        error: err => {
          this.error.set(err.error?.detail ?? 'Failed to create scraping session');
          this.loading.set(false);
        },
      });
  }

  pollSession(id: string): void {
    this.http
      .get<ScrapingSessionResponse>(`/api/scraping-sessions/${id}`)
      .subscribe({
        next: data => {
          this.session.set(data);
          if (TERMINAL_STATUSES.includes(data.status)) {
            this.stopPolling();
          }
        },
        error: () => this.stopPolling(),
      });
  }

  private startPolling(id: string): void {
    this.stopPolling();
    this.pollInterval = setInterval(() => this.pollSession(id), 3000);
  }

  stopPolling(): void {
    if (this.pollInterval !== undefined) {
      clearInterval(this.pollInterval);
      this.pollInterval = undefined;
    }
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/src/app/core/
git commit -m "feat(frontend): add signal-based ListingsService and ScrapingService"
```

---

### Task 5: Shared components — EuroPricePipe and StatusBadgeComponent

**Files:**
- Create: `hermes-frontend/src/app/shared/euro-price.pipe.ts`
- Create: `hermes-frontend/src/app/shared/euro-price.pipe.spec.ts`
- Create: `hermes-frontend/src/app/shared/status-badge.component.ts`
- Create: `hermes-frontend/src/app/shared/status-badge.component.spec.ts`

- [ ] **Step 1: Create `src/app/shared/euro-price.pipe.spec.ts`**

```typescript
import { EuroPricePipe } from './euro-price.pipe';

describe('EuroPricePipe', () => {
  const pipe = new EuroPricePipe();

  it('formats a whole number in Dutch locale', () => {
    expect(pipe.transform(450000)).toBe('€ 450.000');
  });

  it('returns dash for null', () => {
    expect(pipe.transform(null)).toBe('—');
  });

  it('returns dash for undefined', () => {
    expect(pipe.transform(undefined)).toBe('—');
  });

  it('formats small values', () => {
    expect(pipe.transform(0)).toBe('€ 0');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd hermes-frontend && npx ng test --include="**/euro-price.pipe.spec.ts" --watch=false --browsers=ChromeHeadless
```

Expected: FAILED — `Cannot find module './euro-price.pipe'`

- [ ] **Step 3: Create `src/app/shared/euro-price.pipe.ts`**

```typescript
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'euroPrice', standalone: true })
export class EuroPricePipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '—';
    return '€ ' + value.toLocaleString('nl-NL');
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd hermes-frontend && npx ng test --include="**/euro-price.pipe.spec.ts" --watch=false --browsers=ChromeHeadless
```

Expected: 4 specs, 0 failures.

- [ ] **Step 5: Create `src/app/shared/status-badge.component.spec.ts`**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;

  async function create(status: string): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', status);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('span')!;
  }

  it('applies green class for COMPLETED', async () => {
    const el = await create('COMPLETED');
    expect(el.className).toContain('text-green-700');
  });

  it('applies red class for FAILED', async () => {
    const el = await create('FAILED');
    expect(el.className).toContain('text-red-700');
  });

  it('applies blue class for IN_PROGRESS', async () => {
    const el = await create('IN_PROGRESS');
    expect(el.className).toContain('text-blue-700');
  });

  it('applies grey class for unknown status', async () => {
    const el = await create('UNKNOWN_XYZ');
    expect(el.className).toContain('text-gray-700');
  });
});
```

- [ ] **Step 6: Run the spec to verify it fails**

```bash
cd hermes-frontend && npx ng test --include="**/status-badge.component.spec.ts" --watch=false --browsers=ChromeHeadless
```

Expected: FAILED — `Cannot find module './status-badge.component'`

- [ ] **Step 7: Create `src/app/shared/status-badge.component.ts`**

```typescript
import { Component, computed, input } from '@angular/core';

const STATUS_CLASSES: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  TIMED_OUT: 'bg-red-100 text-red-700',
  FOR_SALE: 'bg-green-100 text-green-700',
  UNDER_OFFER: 'bg-orange-100 text-orange-700',
  SOLD: 'bg-gray-100 text-gray-700',
  WITHDRAWN: 'bg-red-100 text-red-700',
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span [class]="'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ' + badgeClass()">
      {{ status() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly badgeClass = computed(
    () => STATUS_CLASSES[this.status()] ?? 'bg-gray-100 text-gray-700'
  );
}
```

- [ ] **Step 8: Run all shared tests to verify they pass**

```bash
cd hermes-frontend && npx ng test --include="**/shared/**" --watch=false --browsers=ChromeHeadless
```

Expected: 8 specs, 0 failures.

- [ ] **Step 9: Commit**

```bash
git add hermes-frontend/src/app/shared/
git commit -m "feat(frontend): add EuroPricePipe and StatusBadgeComponent with tests"
```

---

### Task 6: App shell — navigation and routes

**Files:**
- Modify: `hermes-frontend/src/app/app.routes.ts`
- Modify: `hermes-frontend/src/app/app.component.ts`
- Rewrite: `hermes-frontend/src/app/app.component.html`
- Modify: `hermes-frontend/src/app/app.component.scss`

- [ ] **Step 1: Update `app.routes.ts`**

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
    path: 'listings/:id/report',
    loadComponent: () =>
      import('./pages/listing-report/listing-report-page.component').then(
        m => m.ListingReportPageComponent
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

- [ ] **Step 2: Update `app.component.ts`**

```typescript
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {}
```

- [ ] **Step 3: Rewrite `app.component.html`**

Replace the entire file with:

```html
<nav class="bg-white border-b border-gray-200 px-6 py-3 flex items-center gap-8">
  <span class="text-lg font-semibold text-gray-900">Hermes</span>
  <a
    routerLink="/listings"
    routerLinkActive="text-blue-600 font-medium"
    [routerLinkActiveOptions]="{ exact: false }"
    class="text-sm text-gray-600 hover:text-gray-900 transition-colors"
  >Listings</a>
  <a
    routerLink="/scraping"
    routerLinkActive="text-blue-600 font-medium"
    class="text-sm text-gray-600 hover:text-gray-900 transition-colors"
  >Scraping</a>
</nav>
<main class="max-w-7xl mx-auto px-6 py-8">
  <router-outlet />
</main>
```

- [ ] **Step 4: Clear `app.component.scss`**

Replace with empty content (remove the Angular placeholder styles):

```scss
```

- [ ] **Step 5: Verify `ng build` succeeds**

The page components don't exist yet so lazy routes will warn but not error at build time. Build should succeed.

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds (may show missing module warnings for the lazy page components — that's acceptable until Tasks 7–10 are done).

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/app.routes.ts hermes-frontend/src/app/app.component.ts hermes-frontend/src/app/app.component.html hermes-frontend/src/app/app.component.scss
git commit -m "feat(frontend): add app shell navigation and lazy-loaded routes"
```

---

### Task 7: Scraping page

**Files:**
- Create: `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`

- [ ] **Step 1: Create `src/app/pages/scraping/scraping-page.component.ts`**

```typescript
import { Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrapingService } from '../../core/scraping.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CreateScrapingSessionRequest, SessionStatus } from '../../core/api.types';

const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

@Component({
  selector: 'app-scraping-page',
  standalone: true,
  imports: [FormsModule, DatePipe, StatusBadgeComponent],
  template: `
    <h1 class="text-2xl font-bold text-gray-900 mb-6">New Scraping Session</h1>

    @if (svc.error()) {
      <div class="mb-4 rounded-md bg-red-50 p-4 text-sm text-red-700">{{ svc.error() }}</div>
    }

    <form (ngSubmit)="submit()" class="space-y-4 max-w-lg">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">City *</label>
        <input
          type="text"
          [(ngModel)]="city"
          name="city"
          required
          [disabled]="isPolling()"
          class="block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
        />
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Min price (€)</label>
          <input type="number" [(ngModel)]="minPrice" name="minPrice" [disabled]="isPolling()"
            class="block w-full rounded-md border-gray-300 shadow-sm sm:text-sm" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Max price (€)</label>
          <input type="number" [(ngModel)]="maxPrice" name="maxPrice" [disabled]="isPolling()"
            class="block w-full rounded-md border-gray-300 shadow-sm sm:text-sm" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Min area (m²)</label>
          <input type="number" [(ngModel)]="minArea" name="minArea" [disabled]="isPolling()"
            class="block w-full rounded-md border-gray-300 shadow-sm sm:text-sm" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Max area (m²)</label>
          <input type="number" [(ngModel)]="maxArea" name="maxArea" [disabled]="isPolling()"
            class="block w-full rounded-md border-gray-300 shadow-sm sm:text-sm" />
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Page limit (1–5) *</label>
        <input type="number" [(ngModel)]="pageLimit" name="pageLimit" min="1" max="5" required
          [disabled]="isPolling()"
          class="block w-full rounded-md border-gray-300 shadow-sm sm:text-sm" />
      </div>

      <button
        type="submit"
        [disabled]="!city || svc.loading() || isPolling()"
        class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        @if (svc.loading()) { Starting... } @else { Start scraping }
      </button>
    </form>

    @if (svc.session(); as session) {
      <div class="mt-8 max-w-lg rounded-lg border border-gray-200 p-4 space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700">Session status</span>
          <app-status-badge [status]="session.status" />
        </div>
        <div class="text-xs text-gray-500 font-mono">{{ session.id }}</div>
        <div class="text-xs text-gray-500">
          Started: {{ session.createdAt | date:'medium' }}
        </div>
        @if (session.completedAt) {
          <div class="text-xs text-gray-500">
            Completed: {{ session.completedAt | date:'medium' }}
          </div>
        }
        @if (isTerminal()) {
          <p class="text-sm font-medium"
            [class]="session.status === 'COMPLETED' ? 'text-green-600' : 'text-red-600'">
            @if (session.status === 'COMPLETED') {
              Scraping completed successfully.
            } @else {
              Scraping {{ session.status.toLowerCase() }}.
            }
          </p>
        }
      </div>
    }
  `,
})
export class ScrapingPageComponent {
  protected readonly svc = inject(ScrapingService);

  city = '';
  minPrice: number | null = null;
  maxPrice: number | null = null;
  minArea: number | null = null;
  maxArea: number | null = null;
  pageLimit = 3;

  protected readonly isPolling = computed(() => {
    const s = this.svc.session();
    return s !== null && !TERMINAL_STATUSES.includes(s.status);
  });

  protected readonly isTerminal = computed(() => {
    const s = this.svc.session();
    return s !== null && TERMINAL_STATUSES.includes(s.status);
  });

  submit(): void {
    if (!this.city) return;
    const req: CreateScrapingSessionRequest = {
      city: this.city,
      pageLimit: this.pageLimit,
      ...(this.minPrice != null && { minPrice: this.minPrice }),
      ...(this.maxPrice != null && { maxPrice: this.maxPrice }),
      ...(this.minArea != null && { minArea: this.minArea }),
      ...(this.maxArea != null && { maxArea: this.maxArea }),
    };
    this.svc.createSession(req);
  }
}
```

- [ ] **Step 2: Verify build**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/src/app/pages/scraping/
git commit -m "feat(frontend): add scraping page with session form and live status polling"
```

---

### Task 8: Listings page

**Files:**
- Create: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`

- [ ] **Step 1: Create `src/app/pages/listings/listings-page.component.ts`**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ListingsService } from '../../core/listings.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EuroPricePipe } from '../../shared/euro-price.pipe';

@Component({
  selector: 'app-listings-page',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, EuroPricePipe],
  template: `
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Listings</h1>

    @if (svc.error()) {
      <div class="rounded-md bg-red-50 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
    }

    @if (svc.loading()) {
      <div class="text-sm text-gray-500">Loading...</div>
    } @else {
      <div class="overflow-x-auto rounded-lg border border-gray-200">
        <table class="min-w-full divide-y divide-gray-200 text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left font-medium text-gray-600">Address</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">City</th>
              <th class="px-4 py-3 text-right font-medium text-gray-600">Price</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">Status</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">First Seen</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 bg-white">
            @for (listing of svc.listings().content; track listing.id) {
              <tr
                class="hover:bg-gray-50 cursor-pointer"
                (click)="navigate(listing.id)"
              >
                <td class="px-4 py-3 text-gray-900">
                  {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
                </td>
                <td class="px-4 py-3 text-gray-600">{{ listing.city }}</td>
                <td class="px-4 py-3 text-right text-gray-900">{{ listing.askingPrice | euroPrice }}</td>
                <td class="px-4 py-3">
                  @if (listing.status) {
                    <app-status-badge [status]="listing.status" />
                  } @else {
                    <span class="text-gray-400">—</span>
                  }
                </td>
                <td class="px-4 py-3 text-gray-600">{{ listing.firstSeenAt | date:'mediumDate' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="px-4 py-8 text-center text-gray-400">No listings found</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="mt-4 flex items-center justify-between">
        <div class="flex items-center gap-2 text-sm text-gray-600">
          Rows per page:
          <select
            [(ngModel)]="pageSize"
            (ngModelChange)="onPageSizeChange()"
            class="rounded border-gray-300 text-sm"
          >
            <option [ngValue]="10">10</option>
            <option [ngValue]="20">20</option>
            <option [ngValue]="50">50</option>
          </select>
        </div>
        <div class="flex items-center gap-4 text-sm text-gray-600">
          <span>Page {{ currentPage + 1 }} of {{ svc.listings().totalPages || 1 }}</span>
          <button
            [disabled]="currentPage === 0"
            (click)="prev()"
            class="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
          >Prev</button>
          <button
            [disabled]="currentPage >= svc.listings().totalPages - 1"
            (click)="next()"
            class="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
          >Next</button>
        </div>
      </div>
    }
  `,
})
export class ListingsPageComponent implements OnInit {
  protected readonly svc = inject(ListingsService);
  private readonly router = inject(Router);

  currentPage = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  onPageSizeChange(): void {
    this.currentPage = 0;
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  prev(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.svc.loadListings(this.currentPage, this.pageSize);
    }
  }

  next(): void {
    this.currentPage++;
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  navigate(id: string): void {
    this.router.navigate(['/listings', id]);
  }
}
```

- [ ] **Step 2: Verify build**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/src/app/pages/listings/
git commit -m "feat(frontend): add listings page with paginated table"
```

---

### Task 9: Listing detail page

**Files:**
- Create: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`

- [ ] **Step 1: Create `src/app/pages/listing-detail/listing-detail-page.component.ts`**

```typescript
import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ListingsService } from '../../core/listings.service';
import { ScrapingSessionResponse, SessionStatus } from '../../core/api.types';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

@Component({
  selector: 'app-listing-detail-page',
  standalone: true,
  imports: [DatePipe, RouterLink, EuroPricePipe, StatusBadgeComponent],
  template: `
    @if (svc.error() === '404') {
      <p class="text-gray-500">Listing not found.</p>
    } @else {
      @if (svc.error()) {
        <div class="rounded-md bg-red-50 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
      }

      @if (svc.loading()) {
        <div class="text-sm text-gray-500">Loading...</div>
      }

      @if (svc.currentListing(); as listing) {
        <div class="mb-4">
          <a routerLink="/listings" class="text-sm text-blue-600 hover:underline">← All listings</a>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
          <!-- Left column: address + snapshot -->
          <div class="space-y-6">
            <div>
              <h1 class="text-2xl font-bold text-gray-900">
                {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
              </h1>
              <p class="text-gray-600 mt-1">
                {{ listing.zipCode }} {{ listing.city }}, {{ listing.province }}
              </p>
            </div>

            @if (listing.latestSnapshot; as snap) {
              <div class="rounded-lg border border-gray-200 p-4 space-y-3">
                <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Latest snapshot</h2>
                <div class="grid grid-cols-2 gap-2 text-sm">
                  <span class="text-gray-500">Price</span>
                  <span class="font-medium">{{ snap.askingPrice | euroPrice }}</span>
                  <span class="text-gray-500">Area</span>
                  <span class="font-medium">{{ snap.livingAreaM2 != null ? snap.livingAreaM2 + ' m²' : '—' }}</span>
                  <span class="text-gray-500">Rooms</span>
                  <span class="font-medium">{{ snap.rooms ?? '—' }}</span>
                  <span class="text-gray-500">Energy label</span>
                  <span class="font-medium">{{ snap.energyLabel ?? '—' }}</span>
                  <span class="text-gray-500">Listed since</span>
                  <span class="font-medium">{{ snap.listedOnFundaSince ?? '—' }}</span>
                  <span class="text-gray-500">Status</span>
                  <span>
                    @if (snap.status) {
                      <app-status-badge [status]="snap.status" />
                    } @else {
                      —
                    }
                  </span>
                </div>
                <div class="text-xs text-gray-400">
                  Scraped {{ snap.scrapedAt | date:'medium' }}
                </div>
              </div>
            }

            <a
              [routerLink]="['/listings', listing.id, 'report']"
              class="inline-flex items-center text-sm text-blue-600 hover:underline"
            >
              View report →
            </a>
          </div>

          <!-- Right column: AI summary + rescrape -->
          <div class="space-y-6">
            <div class="rounded-lg border border-gray-200 p-4">
              <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">AI Summary</h2>
              @if (svc.summary(); as summary) {
                <p class="text-sm text-gray-700 leading-relaxed">{{ summary.summary }}</p>
                <p class="text-xs text-gray-400 mt-2">
                  Generated {{ summary.generatedAt | date:'medium' }}
                </p>
              } @else {
                <div class="space-y-2">
                  <div class="h-3 bg-gray-100 rounded animate-pulse"></div>
                  <div class="h-3 bg-gray-100 rounded animate-pulse w-4/5"></div>
                  <div class="h-3 bg-gray-100 rounded animate-pulse w-3/5"></div>
                </div>
              }
            </div>

            <div class="rounded-lg border border-gray-200 p-4 space-y-3">
              <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Rescrape</h2>
              <button
                (click)="triggerRescrape()"
                [disabled]="rescrapeLoading() || isRescrapePolling()"
                class="rounded-md bg-gray-800 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700 disabled:opacity-50"
              >
                @if (rescrapeLoading()) { Triggering... }
                @else if (isRescrapePolling()) { In progress... }
                @else { Trigger rescrape }
              </button>

              @if (rescrapeSession(); as s) {
                <div class="flex items-center gap-2 text-sm">
                  <span class="text-gray-500">Session:</span>
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

  protected readonly isRescrapePolling = () => {
    const s = this.rescrapeSession();
    return s !== null && !TERMINAL_STATUSES.includes(s.status);
  };

  ngOnInit(): void {
    this.svc.loadListing(this.id);
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

- [ ] **Step 2: Verify build**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/src/app/pages/listing-detail/
git commit -m "feat(frontend): add listing detail page with AI summary and rescrape"
```

---

### Task 10: Listing report page with price history chart

**Files:**
- Modify: `hermes-frontend/package.json`
- Modify: `hermes-frontend/src/app/app.config.ts`
- Create: `hermes-frontend/src/app/pages/listing-report/listing-report-page.component.ts`

- [ ] **Step 1: Install Chart.js and ng2-charts**

```bash
cd hermes-frontend && npm install chart.js ng2-charts
```

Expected: installs without errors.

> **Note:** ng2-charts targets Angular 17+. If Angular 22 peer dependency warnings appear, they are safe to ignore — the library's chart rendering is unaffected. If the chart fails to render, check the browser console for "No provider for [ChartDirective]" — that means `provideCharts` is missing from step 2.

- [ ] **Step 2: Add `provideCharts` to `app.config.ts`**

```typescript
import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withFetch()),
    provideCharts(withDefaultRegisterables()),
  ]
};
```

- [ ] **Step 3: Create `src/app/pages/listing-report/listing-report-page.component.ts`**

```typescript
import { Component, computed, inject, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ListingsService } from '../../core/listings.service';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-listing-report-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, BaseChartDirective, EuroPricePipe, StatusBadgeComponent],
  template: `
    @if (svc.error()) {
      <div class="rounded-md bg-red-50 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
    }

    @if (svc.loading()) {
      <div class="text-sm text-gray-500">Loading...</div>
    }

    @if (svc.report(); as report) {
      <div class="mb-4">
        <a [routerLink]="['/listings', report.listingId]" class="text-sm text-blue-600 hover:underline">← Back to listing</a>
      </div>

      <h1 class="text-2xl font-bold text-gray-900 mb-6">Listing Report</h1>

      <!-- Stats row -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div class="rounded-lg border border-gray-200 p-4">
          <p class="text-xs text-gray-500 uppercase tracking-wide">Days on Funda</p>
          <p class="text-2xl font-bold text-gray-900 mt-1">{{ report.daysListedOnFunda ?? '—' }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 p-4">
          <p class="text-xs text-gray-500 uppercase tracking-wide">Days in Hermes</p>
          <p class="text-2xl font-bold text-gray-900 mt-1">{{ report.daysInHermes }}</p>
        </div>
        <div class="rounded-lg border border-gray-200 p-4">
          <p class="text-xs text-gray-500 uppercase tracking-wide">Price change</p>
          @if (report.priceChangePct != null) {
            <p class="text-2xl font-bold mt-1"
              [class]="report.priceChangePct <= 0 ? 'text-green-600' : 'text-red-600'">
              {{ report.priceChangePct | number:'1.1-1' }}%
            </p>
          } @else {
            <p class="text-2xl font-bold text-gray-900 mt-1">—</p>
          }
        </div>
        <div class="rounded-lg border border-gray-200 p-4">
          <p class="text-xs text-gray-500 uppercase tracking-wide">Current price</p>
          <p class="text-2xl font-bold text-gray-900 mt-1">{{ report.currentPrice | euroPrice }}</p>
        </div>
      </div>

      <!-- Price history chart -->
      @if (report.priceHistory.length > 0) {
        <div class="rounded-lg border border-gray-200 p-4 mb-8">
          <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-4">Price history</h2>
          <canvas baseChart [data]="chartData()" [options]="chartOptions" type="line"></canvas>
        </div>
      }

      <!-- Status history -->
      @if (report.statusHistory.length > 0) {
        <div class="rounded-lg border border-gray-200 p-4">
          <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-4">Status history</h2>
          <ol class="space-y-2">
            @for (point of report.statusHistory; track point.scrapedAt) {
              <li class="flex items-center gap-3 text-sm">
                <span class="text-gray-400 tabular-nums">{{ point.scrapedAt | date:'mediumDate' }}</span>
                <app-status-badge [status]="point.status" />
              </li>
            }
          </ol>
        </div>
      }
    }
  `,
})
export class ListingReportPageComponent implements OnInit {
  protected readonly svc = inject(ListingsService);
  private readonly route = inject(ActivatedRoute);

  protected readonly chartOptions: ChartOptions<'line'> = {
    responsive: true,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: ctx =>
            `€ ${ctx.parsed.y != null ? ctx.parsed.y.toLocaleString('nl-NL') : '—'}`,
        },
      },
    },
    scales: {
      y: {
        ticks: {
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
        new Date(p.scrapedAt).toLocaleDateString('nl-NL')
      ),
      datasets: [
        {
          label: 'Asking price',
          data: report.priceHistory.map(p => p.askingPrice ?? null),
          borderColor: 'rgb(59, 130, 246)',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          fill: true,
          tension: 0.3,
          spanGaps: true,
        },
      ],
    };
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.loadReport(id);
  }
}
```

- [ ] **Step 4: Verify the full build**

```bash
cd hermes-frontend && npm run build
```

Expected: build succeeds with no errors.

- [ ] **Step 5: Run all tests**

```bash
cd hermes-frontend && npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: all specs pass (EuroPricePipe × 4, StatusBadgeComponent × 4, AppComponent × 1 = 9 specs, 0 failures).

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/package.json hermes-frontend/package-lock.json hermes-frontend/src/app/app.config.ts hermes-frontend/src/app/pages/listing-report/
git commit -m "feat(frontend): add listing report page with price history chart"
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Covered |
|---|---|
| Angular 22, zoneless | ✅ Task 1 — `provideZonelessChangeDetection()`, no zone.js |
| Tailwind CSS 4, CSS-only config | ✅ Task 2 — `@import "tailwindcss"`, `postcss.config.mjs` |
| `provideHttpClient(withFetch())` | ✅ Task 1 step 5 |
| Dev proxy `/api → localhost:8080` | ✅ Task 3 |
| API type interfaces | ✅ Task 3 `api.types.ts` |
| `ListingsService` signals + methods | ✅ Task 4 |
| `ScrapingService` signals + polling | ✅ Task 4 |
| `EuroPricePipe` with tests | ✅ Task 5 |
| `StatusBadgeComponent` with tests + all 9 statuses | ✅ Task 5 |
| Routing — all 4 routes + redirect | ✅ Task 6 |
| Nav shell | ✅ Task 6 |
| Scraping page — form + live status card | ✅ Task 7 |
| Listings page — table + pagination + page size | ✅ Task 8 |
| Listing detail — two-column, snapshot, AI summary skeleton, rescrape | ✅ Task 9 |
| Listing report — 4 stat cards, line chart, status history | ✅ Task 10 |
| ng2-charts + Chart.js | ✅ Task 10 |
| Error banners on pages | ✅ Tasks 7, 8, 9, 10 |
| 404 shown as "Not found" on detail page | ✅ Task 9 — backend returns 404 detail = null; error banner shows the message |

### Type consistency check

- `SessionStatus` is defined in `api.types.ts` and used as-is in `ScrapingService`, `ScrapingPageComponent`, and `ListingDetailPageComponent` — consistent.
- `TERMINAL_STATUSES` constant is duplicated in `ScrapingService`, `ScrapingPageComponent`, and `ListingDetailPageComponent`. This is intentional — each file has a local reason to know terminal statuses, and the duplication is small. Centralising it in `api.types.ts` is an acceptable future refactor but not needed now.
- `EuroPricePipe` used as `| euroPrice` in templates; pipe name matches `@Pipe({ name: 'euroPrice' })` — consistent.
- `app-status-badge` selector matches `selector: 'app-status-badge'` — consistent.
- `svc.listings().content` accessed in listings page — `ListingPage.content` is typed `ListingSummaryResponse[]` — consistent.
- `chartData()` returns `ChartData<'line'>` and is passed to `[data]` on `<canvas type="line">` — consistent.

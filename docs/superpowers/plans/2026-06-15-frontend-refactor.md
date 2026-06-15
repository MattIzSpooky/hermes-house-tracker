# Frontend Refactor — Template Externalization & Shared Components

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all inline Angular templates to `.html` files and introduce four reusable shared components (`SpinnerComponent`, `ErrorAlertComponent`, `StatCardComponent`, `SectionCardComponent`) to eliminate repeated markup.

**Architecture:** New shared components live in `src/app/shared/`. Each page component loses its inline `template` string in favour of a sibling `.html` file via `templateUrl`. No service, routing, or business logic changes.

**Tech Stack:** Angular 22 (standalone components, signal inputs), Tailwind CSS, Karma/Jasmine, `provideZonelessChangeDetection`.

---

## File Map

**Create:**
- `hermes-frontend/src/app/shared/spinner.component.ts`
- `hermes-frontend/src/app/shared/spinner.component.html`
- `hermes-frontend/src/app/shared/spinner.component.spec.ts`
- `hermes-frontend/src/app/shared/error-alert.component.ts`
- `hermes-frontend/src/app/shared/error-alert.component.html`
- `hermes-frontend/src/app/shared/error-alert.component.spec.ts`
- `hermes-frontend/src/app/shared/stat-card.component.ts`
- `hermes-frontend/src/app/shared/stat-card.component.html`
- `hermes-frontend/src/app/shared/stat-card.component.spec.ts`
- `hermes-frontend/src/app/shared/section-card.component.ts`
- `hermes-frontend/src/app/shared/section-card.component.html`
- `hermes-frontend/src/app/shared/section-card.component.spec.ts`
- `hermes-frontend/src/app/shared/status-badge.component.html`
- `hermes-frontend/src/app/pages/listings/listings-page.component.html`
- `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html`
- `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`

**Modify:**
- `hermes-frontend/src/app/shared/status-badge.component.ts` — switch to `templateUrl`
- `hermes-frontend/src/app/pages/listings/listings-page.component.ts` — switch to `templateUrl`, add new shared component imports
- `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts` — switch to `templateUrl`, add new shared component imports
- `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts` — switch to `templateUrl`, add new shared component imports

---

## Task 1: SpinnerComponent

**Files:**
- Create: `hermes-frontend/src/app/shared/spinner.component.spec.ts`
- Create: `hermes-frontend/src/app/shared/spinner.component.ts`
- Create: `hermes-frontend/src/app/shared/spinner.component.html`

- [ ] **Step 1: Write the failing test**

Create `hermes-frontend/src/app/shared/spinner.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SpinnerComponent } from './spinner.component';

describe('SpinnerComponent', () => {
  let fixture: ComponentFixture<SpinnerComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpinnerComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(SpinnerComponent);
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should render the animated spinner element', async () => {
    expect(el.querySelector('.animate-spin')).toBeTruthy();
  });

  it('should use cyan border by default', async () => {
    expect(el.querySelector('.border-cyan-500')).toBeTruthy();
  });

  it('should use white border when color is white', async () => {
    fixture.componentRef.setInput('color', 'white');
    await fixture.whenStable();
    expect(el.querySelector('.border-white')).toBeTruthy();
    expect(el.querySelector('.border-cyan-500')).toBeNull();
  });

  it('should show label text when provided', async () => {
    fixture.componentRef.setInput('label', 'Loading...');
    await fixture.whenStable();
    expect(el.textContent?.trim()).toContain('Loading...');
  });

  it('should show no label text by default', async () => {
    expect(el.textContent?.trim()).toBe('');
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: test fails with "Cannot find module './spinner.component'"

- [ ] **Step 3: Create `spinner.component.ts`**

```typescript
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  standalone: true,
  templateUrl: './spinner.component.html',
})
export class SpinnerComponent {
  readonly color = input<'cyan' | 'white'>('cyan');
  readonly label = input<string>('');
}
```

- [ ] **Step 4: Create `spinner.component.html`**

```html
<span class="inline-flex items-center gap-2">
  <span class="inline-block w-4 h-4 border-2 border-t-transparent rounded-full animate-spin"
    [class.border-cyan-500]="color() === 'cyan'"
    [class.border-white]="color() === 'white'"></span>
  @if (label()) {
    {{ label() }}
  }
</span>
```

- [ ] **Step 5: Run test and verify it passes**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: all `SpinnerComponent` tests pass.

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/shared/spinner.component.ts \
        hermes-frontend/src/app/shared/spinner.component.html \
        hermes-frontend/src/app/shared/spinner.component.spec.ts
git commit -m "feat(hermes-frontend): add SpinnerComponent"
```

---

## Task 2: ErrorAlertComponent

**Files:**
- Create: `hermes-frontend/src/app/shared/error-alert.component.spec.ts`
- Create: `hermes-frontend/src/app/shared/error-alert.component.ts`
- Create: `hermes-frontend/src/app/shared/error-alert.component.html`

- [ ] **Step 1: Write the failing test**

Create `hermes-frontend/src/app/shared/error-alert.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ErrorAlertComponent } from './error-alert.component';

describe('ErrorAlertComponent', () => {
  let fixture: ComponentFixture<ErrorAlertComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorAlertComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(ErrorAlertComponent);
    fixture.componentRef.setInput('message', 'Something went wrong');
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should display the error message', async () => {
    expect(el.textContent?.trim()).toContain('Something went wrong');
  });

  it('should have red error styling', async () => {
    expect(el.querySelector('.bg-red-50')).toBeTruthy();
    expect(el.querySelector('.text-red-700')).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: test fails with "Cannot find module './error-alert.component'"

- [ ] **Step 3: Create `error-alert.component.ts`**

```typescript
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-error-alert',
  standalone: true,
  templateUrl: './error-alert.component.html',
})
export class ErrorAlertComponent {
  readonly message = input.required<string>();
}
```

- [ ] **Step 4: Create `error-alert.component.html`**

```html
<div class="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-4">
  {{ message() }}
</div>
```

- [ ] **Step 5: Run test and verify it passes**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: all `ErrorAlertComponent` tests pass.

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/shared/error-alert.component.ts \
        hermes-frontend/src/app/shared/error-alert.component.html \
        hermes-frontend/src/app/shared/error-alert.component.spec.ts
git commit -m "feat(hermes-frontend): add ErrorAlertComponent"
```

---

## Task 3: StatCardComponent

**Files:**
- Create: `hermes-frontend/src/app/shared/stat-card.component.spec.ts`
- Create: `hermes-frontend/src/app/shared/stat-card.component.ts`
- Create: `hermes-frontend/src/app/shared/stat-card.component.html`

- [ ] **Step 1: Write the failing test**

Content projection requires a host component. Create `hermes-frontend/src/app/shared/stat-card.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StatCardComponent } from './stat-card.component';

@Component({
  standalone: true,
  imports: [StatCardComponent],
  template: `<app-stat-card label="Days in Hermes"><span class="value">42</span></app-stat-card>`,
})
class TestHostComponent {}

describe('StatCardComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(TestHostComponent);
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should display the label', async () => {
    expect(el.textContent).toContain('Days in Hermes');
  });

  it('should project content into the card', async () => {
    const projected = el.querySelector('.value');
    expect(projected).toBeTruthy();
    expect(projected?.textContent?.trim()).toBe('42');
  });

  it('should render the card container', async () => {
    expect(el.querySelector('.bg-white.rounded-xl')).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: test fails with "Cannot find module './stat-card.component'"

- [ ] **Step 3: Create `stat-card.component.ts`**

```typescript
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  templateUrl: './stat-card.component.html',
})
export class StatCardComponent {
  readonly label = input.required<string>();
}
```

- [ ] **Step 4: Create `stat-card.component.html`**

```html
<div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
  <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">{{ label() }}</p>
  <ng-content />
</div>
```

- [ ] **Step 5: Run test and verify it passes**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: all `StatCardComponent` tests pass.

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/shared/stat-card.component.ts \
        hermes-frontend/src/app/shared/stat-card.component.html \
        hermes-frontend/src/app/shared/stat-card.component.spec.ts
git commit -m "feat(hermes-frontend): add StatCardComponent"
```

---

## Task 4: SectionCardComponent

**Files:**
- Create: `hermes-frontend/src/app/shared/section-card.component.spec.ts`
- Create: `hermes-frontend/src/app/shared/section-card.component.ts`
- Create: `hermes-frontend/src/app/shared/section-card.component.html`

- [ ] **Step 1: Write the failing test**

Create `hermes-frontend/src/app/shared/section-card.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SectionCardComponent } from './section-card.component';

@Component({
  standalone: true,
  imports: [SectionCardComponent],
  template: `<app-section-card><p class="content">Hello</p></app-section-card>`,
})
class TestHostComponent {}

describe('SectionCardComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(TestHostComponent);
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should render the card shell', async () => {
    expect(el.querySelector('.bg-white.rounded-xl')).toBeTruthy();
  });

  it('should apply default p-5 padding', async () => {
    expect(el.querySelector('.p-5')).toBeTruthy();
  });

  it('should project content', async () => {
    expect(el.querySelector('.content')).toBeTruthy();
  });
});

describe('SectionCardComponent — inputs', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('should apply custom padding when provided', async () => {
    @Component({
      standalone: true,
      imports: [SectionCardComponent],
      template: `<app-section-card padding="p-6"><p>X</p></app-section-card>`,
    })
    class Host {}
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const f = TestBed.createComponent(Host);
    await f.whenStable();
    expect(f.nativeElement.querySelector('.p-6')).toBeTruthy();
  });

  it('should apply extraClass to the inner card div', async () => {
    @Component({
      standalone: true,
      imports: [SectionCardComponent],
      template: `<app-section-card extraClass="space-y-4"><p>X</p></app-section-card>`,
    })
    class Host {}
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const f = TestBed.createComponent(Host);
    await f.whenStable();
    expect(f.nativeElement.querySelector('.space-y-4')).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: test fails with "Cannot find module './section-card.component'"

- [ ] **Step 3: Create `section-card.component.ts`**

```typescript
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-section-card',
  standalone: true,
  templateUrl: './section-card.component.html',
})
export class SectionCardComponent {
  readonly padding = input<string>('p-5');
  readonly extraClass = input<string>('');
}
```

- [ ] **Step 4: Create `section-card.component.html`**

```html
<div [class]="'bg-white rounded-xl border border-slate-200 shadow-sm ' + padding() + (extraClass() ? ' ' + extraClass() : '')">
  <ng-content />
</div>
```

- [ ] **Step 5: Run test and verify it passes**

```
cd hermes-frontend && npm test -- --watch=false
```

Expected: all `SectionCardComponent` tests pass.

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/shared/section-card.component.ts \
        hermes-frontend/src/app/shared/section-card.component.html \
        hermes-frontend/src/app/shared/section-card.component.spec.ts
git commit -m "feat(hermes-frontend): add SectionCardComponent"
```

---

## Task 5: Externalize status-badge template

**Files:**
- Create: `hermes-frontend/src/app/shared/status-badge.component.html`
- Modify: `hermes-frontend/src/app/shared/status-badge.component.ts`

- [ ] **Step 1: Create `status-badge.component.html`**

Move the template string content verbatim into the new file:

```html
<span [class]="'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ' + badgeClass()">
  {{ label() }}
</span>
```

- [ ] **Step 2: Update `status-badge.component.ts`**

Replace `template: \`...\`` with `templateUrl`:

```typescript
import { Component, computed, input } from '@angular/core';

const STATUS_CLASSES: Record<string, string> = {
  PENDING:      'bg-slate-100 text-slate-600',
  IN_PROGRESS:  'bg-cyan-100 text-cyan-700',
  COMPLETED:    'bg-emerald-100 text-emerald-700',
  FAILED:       'bg-red-100 text-red-700',
  TIMED_OUT:    'bg-red-100 text-red-700',
  FOR_SALE:     'bg-emerald-100 text-emerald-700',
  UNDER_OFFER:  'bg-amber-100 text-amber-700',
  SOLD:         'bg-slate-100 text-slate-500',
  WITHDRAWN:    'bg-red-100 text-red-700',
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  templateUrl: './status-badge.component.html',
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly badgeClass = computed(
    () => STATUS_CLASSES[this.status()] ?? 'bg-slate-100 text-slate-600'
  );
  protected readonly label = computed(() =>
    this.status().replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
  );
}
```

- [ ] **Step 3: Build to verify compilation**

```
cd hermes-frontend && npx ng build
```

Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/shared/status-badge.component.ts \
        hermes-frontend/src/app/shared/status-badge.component.html
git commit -m "refactor(hermes-frontend): externalize status-badge template"
```

---

## Task 6: Refactor listings-page

**Files:**
- Create: `hermes-frontend/src/app/pages/listings/listings-page.component.html`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`

- [ ] **Step 1: Create `listings-page.component.html`**

```html
<div class="mb-6">
  <h1 class="text-2xl font-bold text-slate-900">Listings</h1>
  <p class="text-sm text-slate-500 mt-0.5">All tracked properties</p>
</div>

<app-section-card padding="p-4" class="mb-4">
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
</app-section-card>

@if (svc.error()) {
  <app-error-alert [message]="svc.error()!" />
}

@if (svc.loading()) {
  <div class="text-sm text-slate-500">
    <app-spinner label="Loading listings..." />
  </div>
} @else {
  <app-section-card padding="" extraClass="overflow-hidden">
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
  </app-section-card>

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
```

- [ ] **Step 2: Update `listings-page.component.ts`**

Replace `template` with `templateUrl` and add the three new imports:

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
import { SpinnerComponent } from '../../shared/spinner.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';

@Component({
  selector: 'app-listings-page',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, EuroPricePipe, SpinnerComponent, ErrorAlertComponent, SectionCardComponent],
  templateUrl: './listings-page.component.html',
})
export class ListingsPageComponent implements OnInit, OnDestroy {
  // (class body unchanged)
```

Only the decorator changes — the class body (everything from `protected readonly svc` through `loadWithFilters`) stays exactly as-is.

- [ ] **Step 3: Build to verify compilation**

```
cd hermes-frontend && npx ng build
```

Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/listings/listings-page.component.ts \
        hermes-frontend/src/app/pages/listings/listings-page.component.html
git commit -m "refactor(hermes-frontend): externalize listings-page template, use shared components"
```

---

## Task 7: Refactor listing-detail-page

**Files:**
- Create: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html`
- Modify: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`

- [ ] **Step 1: Create `listing-detail-page.component.html`**

```html
@if (svc.error() === '404') {
  <div class="rounded-xl bg-white border border-slate-200 shadow-sm p-12 text-center">
    <p class="text-slate-500 font-medium">Listing not found</p>
    <a routerLink="/listings" class="mt-4 inline-block text-sm text-cyan-600 hover:text-cyan-500 font-medium">← Back to listings</a>
  </div>
} @else {
  @if (svc.error()) {
    <app-error-alert [message]="svc.error()!" />
  }

  @if (svc.loading()) {
    <div class="text-sm text-slate-500">
      <app-spinner label="Loading..." />
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

    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
      <app-stat-card label="Days in Hermes">
        <p class="text-3xl font-bold text-cyan-500 mt-2 tabular-nums">
          {{ svc.report()?.daysInHermes ?? '—' }}
        </p>
      </app-stat-card>
      <app-stat-card label="Price change">
        @if (svc.report(); as report) {
          @if (report.priceChangePct != null) {
            <p class="text-3xl font-bold mt-2 tabular-nums"
              [class]="report.priceChangePct <= 0 ? 'text-emerald-500' : 'text-red-500'">
              {{ report.priceChangePct | number:'1.1-1' }}%
            </p>
          } @else {
            <p class="text-3xl font-bold text-slate-300 mt-2">—</p>
          }
        } @else {
          <p class="text-3xl font-bold text-slate-300 mt-2">—</p>
        }
      </app-stat-card>
      <app-stat-card label="Current price">
        <p class="text-3xl font-bold text-slate-900 mt-2 tabular-nums">{{ listing.currentPrice | euroPrice }}</p>
      </app-stat-card>
      <app-stat-card label="Status">
        @if (listing.status) {
          <div class="mt-2"><app-status-badge [status]="listing.status" /></div>
        } @else {
          <p class="text-slate-300 mt-2">—</p>
        }
      </app-stat-card>
    </div>

    @if (svc.report(); as report) {
      @if (report.priceHistory.length > 0) {
        <app-section-card class="mb-6">
          <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-5">Price history</h2>
          <canvas baseChart [data]="chartData()" [options]="chartOptions" type="line"></canvas>
        </app-section-card>
      }
    }

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <app-section-card extraClass="space-y-3">
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
      </app-section-card>

      <div class="space-y-5">
        <app-section-card>
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
        </app-section-card>

        <app-section-card extraClass="space-y-4">
          <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Rescrape</h2>
          <button (click)="triggerRescrape()" [disabled]="rescrapeLoading() || isRescrapePolling()"
            class="rounded-lg bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white
                   hover:bg-slate-700 disabled:opacity-50 transition-colors">
            @if (rescrapeLoading()) {
              <app-spinner color="white" label="Triggering..." />
            } @else if (isRescrapePolling()) {
              <app-spinner color="white" label="In progress..." />
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
        </app-section-card>
      </div>
    </div>
  }
}
```

- [ ] **Step 2: Update `listing-detail-page.component.ts`**

Replace `template` with `templateUrl` and add the four new imports:

```typescript
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ListingsService } from '../../core/listings.service';
import { ScrapingSessionResponse, TERMINAL_STATUSES } from '../../core/api.types';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { SpinnerComponent } from '../../shared/spinner.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { StatCardComponent } from '../../shared/stat-card.component';
import { SectionCardComponent } from '../../shared/section-card.component';

@Component({
  selector: 'app-listing-detail-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, BaseChartDirective, EuroPricePipe, StatusBadgeComponent, SpinnerComponent, ErrorAlertComponent, StatCardComponent, SectionCardComponent],
  templateUrl: './listing-detail-page.component.html',
})
export class ListingDetailPageComponent implements OnInit, OnDestroy {
  // (class body unchanged)
```

Only the decorator changes — the class body stays exactly as-is.

- [ ] **Step 3: Build to verify compilation**

```
cd hermes-frontend && npx ng build
```

Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts \
        hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html
git commit -m "refactor(hermes-frontend): externalize listing-detail-page template, use shared components"
```

---

## Task 8: Refactor scraping-page

**Files:**
- Create: `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`

- [ ] **Step 1: Create `scraping-page.component.html`**

```html
<div class="mb-6">
  <h1 class="text-2xl font-bold text-slate-900">Scraping</h1>
  <p class="text-sm text-slate-500 mt-0.5">Start a new Funda search session</p>
</div>

@if (svc.error()) {
  <app-error-alert [message]="svc.error()!" />
}

<app-section-card padding="p-6" extraClass="max-w-lg">
  <form (ngSubmit)="submit()" class="space-y-5">
    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">
        City <span class="text-cyan-500">*</span>
      </label>
      <input type="text" [(ngModel)]="city" name="city" required [disabled]="isPolling()"
        placeholder="e.g. Amsterdam"
        class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
               focus:border-cyan-500 focus:ring-cyan-500
               disabled:bg-slate-50 disabled:text-slate-400" />
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Min price (€)</label>
        <input type="number" [(ngModel)]="minPrice" name="minPrice" [disabled]="isPolling()"
          placeholder="200 000"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500 disabled:bg-slate-50" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Max price (€)</label>
        <input type="number" [(ngModel)]="maxPrice" name="maxPrice" [disabled]="isPolling()"
          placeholder="600 000"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500 disabled:bg-slate-50" />
      </div>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Min area (m²)</label>
        <input type="number" [(ngModel)]="minArea" name="minArea" [disabled]="isPolling()"
          placeholder="60"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500 disabled:bg-slate-50" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Max area (m²)</label>
        <input type="number" [(ngModel)]="maxArea" name="maxArea" [disabled]="isPolling()"
          placeholder="200"
          class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
                 focus:border-cyan-500 focus:ring-cyan-500 disabled:bg-slate-50" />
      </div>
    </div>

    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">
        Page limit <span class="text-cyan-500">*</span>
      </label>
      <input type="number" [(ngModel)]="pageLimit" name="pageLimit" min="1" max="5" required
        [disabled]="isPolling()"
        class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
               focus:border-cyan-500 focus:ring-cyan-500 disabled:bg-slate-50" />
      <p class="mt-1.5 text-xs text-slate-400">1 to 5 pages per scrape</p>
    </div>

    <button type="submit" [disabled]="!city || svc.loading() || isPolling()"
      class="w-full rounded-lg bg-cyan-500 px-4 py-2.5 text-sm font-semibold text-white
             hover:bg-cyan-400 disabled:opacity-50 transition-colors">
      @if (svc.loading()) {
        <app-spinner color="white" label="Starting..." />
      } @else {
        Start scraping
      }
    </button>
  </form>
</app-section-card>

@if (svc.session(); as session) {
  <app-section-card class="mt-6" extraClass="max-w-lg space-y-4">
    <div class="flex items-center justify-between">
      <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Session</h2>
      <app-status-badge [status]="session.status" />
    </div>
    <div class="font-mono text-xs text-slate-400 bg-slate-50 rounded-lg px-3 py-2 break-all">{{ session.id }}</div>
    <div class="space-y-1 text-xs text-slate-500">
      <div>Started: <span class="text-slate-700">{{ session.createdAt | date:'medium' }}</span></div>
      @if (session.completedAt) {
        <div>Completed: <span class="text-slate-700">{{ session.completedAt | date:'medium' }}</span></div>
      }
    </div>
    @if (isPolling()) {
      <div class="text-sm text-cyan-600 font-medium">
        <app-spinner label="Scraping in progress..." />
      </div>
    }
    @if (isTerminal()) {
      <div class="rounded-lg px-4 py-3 text-sm font-medium border"
        [class]="session.status === 'COMPLETED'
          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
          : 'bg-red-50 text-red-700 border-red-200'">
        @if (session.status === 'COMPLETED') {
          Scraping completed successfully.
        } @else if (session.status === 'TIMED_OUT') {
          Scraping timed out.
        } @else {
          Scraping failed.
        }
      </div>
    }
  </app-section-card>
}
```

- [ ] **Step 2: Update `scraping-page.component.ts`**

Replace `template` with `templateUrl` and add the three new imports:

```typescript
import { Component, computed, inject, OnDestroy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrapingService } from '../../core/scraping.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CreateScrapingSessionRequest, TERMINAL_STATUSES } from '../../core/api.types';
import { SpinnerComponent } from '../../shared/spinner.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';

@Component({
  selector: 'app-scraping-page',
  standalone: true,
  imports: [FormsModule, DatePipe, StatusBadgeComponent, SpinnerComponent, ErrorAlertComponent, SectionCardComponent],
  templateUrl: './scraping-page.component.html',
})
export class ScrapingPageComponent implements OnDestroy {
  // (class body unchanged)
```

Only the decorator changes — the class body stays exactly as-is.

- [ ] **Step 3: Build to verify compilation**

```
cd hermes-frontend && npx ng build
```

Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/scraping/scraping-page.component.ts \
        hermes-frontend/src/app/pages/scraping/scraping-page.component.html
git commit -m "refactor(hermes-frontend): externalize scraping-page template, use shared components"
```

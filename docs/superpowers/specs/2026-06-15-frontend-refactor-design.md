# Frontend Refactor — Template Externalization & Shared Components

**Date:** 2026-06-15
**Scope:** `hermes-frontend/src/app/`

## Goal

Extract all inline component templates to `.html` files for uniformity, and introduce four reusable shared components to eliminate repeated markup.

## 1. Template Externalization

Every component using `template: \`...\`` is converted to `templateUrl: './component-name.html'`. The HTML content moves verbatim into the new file.

| Component file | New HTML file |
|---|---|
| `shared/status-badge.component.ts` | `shared/status-badge.component.html` |
| `pages/listing-detail/listing-detail-page.component.ts` | `pages/listing-detail/listing-detail-page.component.html` |
| `pages/listings/listings-page.component.ts` | `pages/listings/listings-page.component.html` |
| `pages/scraping/scraping-page.component.ts` | `pages/scraping/scraping-page.component.html` |

`app.component.ts` already uses `templateUrl` — no change needed.

## 2. New Shared Components

All new files live in `src/app/shared/`. All components are standalone.

### SpinnerComponent (`app-spinner`)

Eliminates 5 copy-pasted spinner spans across 3 files.

**Inputs:**
- `color: 'cyan' | 'white'` — defaults to `'cyan'`
- `label: string` — optional text shown next to the spinner

**Usage:**
```html
<app-spinner label="Loading..." />
<app-spinner color="white" label="Starting..." />
```

### ErrorAlertComponent (`app-error-alert`)

Eliminates the repeated red error banner in all 3 pages.

**Inputs:**
- `message: string` — required

**Usage:**
```html
@if (svc.error()) {
  <app-error-alert [message]="svc.error()!" />
}
```

### StatCardComponent (`app-stat-card`)

Replaces the 4 identical metric cards in the listing detail page. The value is projected via `ng-content` so arbitrary content (numbers, badges, formatted text) can be placed inside.

**Inputs:**
- `label: string` — required; the small uppercase header text

**Usage:**
```html
<app-stat-card label="Days in Hermes">
  <p class="text-3xl font-bold text-cyan-500 mt-2 tabular-nums">{{ value }}</p>
</app-stat-card>
```

### SectionCardComponent (`app-section-card`)

A layout-only wrapper for the white card container used throughout all pages. No inputs — content is projected via `ng-content`.

**Renders:** `<div class="bg-white rounded-xl border border-slate-200 shadow-sm ..."><ng-content /></div>`

**Usage:**
```html
<app-section-card>
  <h2 ...>Details</h2>
  ...
</app-section-card>
```

## 3. Final File Structure

```
src/app/shared/
  euro-price.pipe.ts
  euro-price.pipe.spec.ts
  status-badge.component.ts
  status-badge.component.html        ← new
  status-badge.component.spec.ts
  spinner.component.ts               ← new
  spinner.component.html             ← new
  error-alert.component.ts           ← new
  error-alert.component.html         ← new
  stat-card.component.ts             ← new
  stat-card.component.html           ← new
  section-card.component.ts          ← new
  section-card.component.html        ← new
```

Each page component also gets a `.html` sibling file.

## 4. Constraints

- No changes to services, routing, types, or business logic.
- No new abstractions beyond what is described here.
- All components remain standalone (no NgModule).
- Existing `app.component.html` is untouched.

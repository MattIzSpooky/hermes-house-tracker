import { Component, computed, inject, OnDestroy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrapingService } from '../../core/scraping.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CreateScrapingSessionRequest, TERMINAL_STATUSES } from '../../core/api.types';

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
export class ScrapingPageComponent implements OnDestroy {
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

  ngOnDestroy(): void {
    this.svc.stopPolling();
  }

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

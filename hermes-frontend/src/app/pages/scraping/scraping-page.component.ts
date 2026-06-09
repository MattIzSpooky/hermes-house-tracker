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
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-slate-900">Scraping</h1>
      <p class="text-sm text-slate-500 mt-0.5">Start a new Funda search session</p>
    </div>

    @if (svc.error()) {
      <div class="mb-4 rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700">{{ svc.error() }}</div>
    }

    <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6 max-w-lg">
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
            <span class="flex items-center justify-center gap-2">
              <span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              Starting...
            </span>
          } @else {
            Start scraping
          }
        </button>
      </form>
    </div>

    @if (svc.session(); as session) {
      <div class="mt-6 max-w-lg bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
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
          <div class="flex items-center gap-2 text-sm text-cyan-600 font-medium">
            <span class="inline-block w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
            Scraping in progress...
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

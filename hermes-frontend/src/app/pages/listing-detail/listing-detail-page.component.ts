import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ListingsService } from '../../core/listings.service';
import { ScrapingSessionResponse, TERMINAL_STATUSES } from '../../core/api.types';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-listing-detail-page',
  standalone: true,
  imports: [DatePipe, RouterLink, EuroPricePipe, StatusBadgeComponent],
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

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Left column: address + snapshot -->
          <div class="space-y-5">
            <div>
              <h1 class="text-2xl font-bold text-slate-900 leading-tight">
                {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
              </h1>
              <p class="text-slate-500 mt-1">
                {{ listing.zipCode }} {{ listing.city }}, {{ listing.province }}
              </p>
            </div>

            @if (listing.latestSnapshot; as snap) {
              <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
                <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Latest snapshot</h2>
                <div class="grid grid-cols-2 gap-x-4 gap-y-2.5 text-sm">
                  <span class="text-slate-400">Price</span>
                  <span class="font-semibold text-slate-900 tabular-nums">{{ snap.askingPrice | euroPrice }}</span>
                  <span class="text-slate-400">Area</span>
                  <span class="font-medium text-slate-700">{{ snap.livingAreaM2 != null ? snap.livingAreaM2 + ' m²' : '—' }}</span>
                  <span class="text-slate-400">Rooms</span>
                  <span class="font-medium text-slate-700">{{ snap.rooms ?? '—' }}</span>
                  <span class="text-slate-400">Energy label</span>
                  <span class="font-medium text-slate-700">{{ snap.energyLabel ?? '—' }}</span>
                  <span class="text-slate-400">Listed since</span>
                  <span class="font-medium text-slate-700">{{ snap.listedOnFundaSince ?? '—' }}</span>
                  <span class="text-slate-400">Status</span>
                  <span>
                    @if (snap.status) {
                      <app-status-badge [status]="snap.status" />
                    } @else {
                      <span class="text-slate-300">—</span>
                    }
                  </span>
                </div>
                <p class="text-xs text-slate-400 pt-1 border-t border-slate-100">
                  Scraped {{ snap.scrapedAt | date:'medium' }}
                </p>
              </div>
            }

            <a [routerLink]="['/listings', listing.id, 'report']"
              class="inline-flex items-center gap-1.5 text-sm font-medium text-cyan-600 hover:text-cyan-500 transition-colors">
              View full report →
            </a>
          </div>

          <!-- Right column: AI summary + rescrape -->
          <div class="space-y-5">
            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
              <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-4">AI Summary</h2>
              @if (svc.summary(); as summary) {
                <p class="text-sm text-slate-700 leading-relaxed">{{ summary.summary }}</p>
                <p class="text-xs text-slate-400 mt-3">
                  Generated {{ summary.generatedAt | date:'medium' }}
                </p>
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

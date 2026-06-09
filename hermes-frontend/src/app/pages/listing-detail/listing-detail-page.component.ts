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
              } @else if (svc.summaryNotFound()) {
                <p class="text-sm text-gray-400 italic">No summary available.</p>
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

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

import { Component, DestroyRef, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ListingsService } from '../../core/listings.service';
import { FavouritesService } from '../../core/favourites.service';
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
  protected readonly svc = inject(ListingsService);
  protected readonly favourites = inject(FavouritesService);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rescrapeSession = signal<ScrapingSessionResponse | null>(null);
  protected readonly rescrapeLoading = signal(false);
  private pollInterval?: ReturnType<typeof setInterval>;

  protected get id(): string {
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
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = params.get('id')!;
      this.clearPoll();
      this.rescrapeSession.set(null);
      this.svc.loadListingAndReport(id);
      this.svc.loadSummary(id);
    });
  }

  ngOnDestroy(): void {
    this.clearPoll();
    this.svc.clearSummaryPoll();
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

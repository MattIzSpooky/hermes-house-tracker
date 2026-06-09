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

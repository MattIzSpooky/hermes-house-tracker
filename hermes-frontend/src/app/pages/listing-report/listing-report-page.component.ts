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
    @if (svc.error() === '404') {
      <div class="rounded-xl bg-white border border-slate-200 shadow-sm p-12 text-center">
        <p class="text-slate-500 font-medium">Report not found</p>
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

      @if (svc.report(); as report) {
        <div class="mb-5">
          <a [routerLink]="['/listings', report.listingId]" class="text-sm text-cyan-600 hover:text-cyan-500 font-medium">← Back to listing</a>
        </div>

        <div class="mb-6">
          <h1 class="text-2xl font-bold text-slate-900">Report</h1>
        </div>

        <!-- Stats row -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Days on Funda</p>
            <p class="text-3xl font-bold text-slate-900 mt-2 tabular-nums">{{ report.daysListedOnFunda ?? '—' }}</p>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Days in Hermes</p>
            <p class="text-3xl font-bold text-cyan-500 mt-2 tabular-nums">{{ report.daysInHermes }}</p>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Price change</p>
            @if (report.priceChangePct != null) {
              <p class="text-3xl font-bold mt-2 tabular-nums"
                [class]="report.priceChangePct <= 0 ? 'text-emerald-500' : 'text-red-500'">
                {{ report.priceChangePct | number:'1.1-1' }}%
              </p>
            } @else {
              <p class="text-3xl font-bold text-slate-300 mt-2">—</p>
            }
          </div>
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Current price</p>
            <p class="text-3xl font-bold text-slate-900 mt-2 tabular-nums">{{ report.currentPrice | euroPrice }}</p>
          </div>
        </div>

        <!-- Price history chart -->
        @if (report.priceHistory.length > 0) {
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 mb-6">
            <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-5">Price history</h2>
            <canvas baseChart [data]="chartData()" [options]="chartOptions" type="line"></canvas>
          </div>
        }

        <!-- Status history -->
        @if (report.statusHistory.length > 0) {
          <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-4">Status history</h2>
            <ol class="space-y-2.5">
              @for (point of report.statusHistory; track point.scrapedAt) {
                <li class="flex items-center gap-3 text-sm">
                  <span class="text-slate-400 tabular-nums w-28 shrink-0">{{ point.scrapedAt | date:'mediumDate' }}</span>
                  <app-status-badge [status]="point.status" />
                </li>
              }
            </ol>
          </div>
        }
      }
    }
  `,
})
export class ListingReportPageComponent implements OnInit {
  protected readonly svc = inject(ListingsService);
  private readonly route = inject(ActivatedRoute);

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
        new Date(p.scrapedAt).toLocaleDateString('nl-NL')
      ),
      datasets: [
        {
          label: 'Asking price',
          data: report.priceHistory.map(p => p.askingPrice ?? null),
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
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.loadReport(id);
  }
}

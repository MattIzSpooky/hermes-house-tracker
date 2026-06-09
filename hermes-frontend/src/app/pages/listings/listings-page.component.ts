import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ListingsService } from '../../core/listings.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EuroPricePipe } from '../../shared/euro-price.pipe';

@Component({
  selector: 'app-listings-page',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, EuroPricePipe],
  template: `
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-slate-900">Listings</h1>
      <p class="text-sm text-slate-500 mt-0.5">All tracked properties</p>
    </div>

    @if (svc.error()) {
      <div class="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
    }

    @if (svc.loading()) {
      <div class="flex items-center gap-2 text-sm text-slate-500">
        <span class="inline-block w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
        Loading listings...
      </div>
    } @else {
      <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
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
      </div>

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
  `,
})
export class ListingsPageComponent implements OnInit {
  protected readonly svc = inject(ListingsService);
  private readonly router = inject(Router);

  currentPage = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  onPageSizeChange(): void {
    this.currentPage = 0;
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  prev(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.svc.loadListings(this.currentPage, this.pageSize);
    }
  }

  next(): void {
    this.currentPage++;
    this.svc.loadListings(this.currentPage, this.pageSize);
  }

  navigate(id: string): void {
    this.router.navigate(['/listings', id]);
  }
}

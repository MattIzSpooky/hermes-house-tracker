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
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Listings</h1>

    @if (svc.error()) {
      <div class="rounded-md bg-red-50 p-4 text-sm text-red-700 mb-4">{{ svc.error() }}</div>
    }

    @if (svc.loading()) {
      <div class="text-sm text-gray-500">Loading...</div>
    } @else {
      <div class="overflow-x-auto rounded-lg border border-gray-200">
        <table class="min-w-full divide-y divide-gray-200 text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left font-medium text-gray-600">Address</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">City</th>
              <th class="px-4 py-3 text-right font-medium text-gray-600">Price</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">Status</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">First Seen</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 bg-white">
            @for (listing of svc.listings().content; track listing.id) {
              <tr
                class="hover:bg-gray-50 cursor-pointer"
                (click)="navigate(listing.id)"
              >
                <td class="px-4 py-3 text-gray-900">
                  {{ listing.street }} {{ listing.houseNumber }}{{ listing.houseNumberAddition ?? '' }}
                </td>
                <td class="px-4 py-3 text-gray-600">{{ listing.city }}</td>
                <td class="px-4 py-3 text-right text-gray-900">{{ listing.askingPrice | euroPrice }}</td>
                <td class="px-4 py-3">
                  @if (listing.status) {
                    <app-status-badge [status]="listing.status" />
                  } @else {
                    <span class="text-gray-400">—</span>
                  }
                </td>
                <td class="px-4 py-3 text-gray-600">{{ listing.firstSeenAt | date:'mediumDate' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="px-4 py-8 text-center text-gray-400">No listings found</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="mt-4 flex items-center justify-between">
        <div class="flex items-center gap-2 text-sm text-gray-600">
          Rows per page:
          <select
            [(ngModel)]="pageSize"
            (ngModelChange)="onPageSizeChange()"
            class="rounded border-gray-300 text-sm"
          >
            <option [ngValue]="10">10</option>
            <option [ngValue]="20">20</option>
            <option [ngValue]="50">50</option>
          </select>
        </div>
        <div class="flex items-center gap-4 text-sm text-gray-600">
          <span>Page {{ currentPage + 1 }} of {{ svc.listings().totalPages || 1 }}</span>
          <button
            [disabled]="currentPage === 0"
            (click)="prev()"
            class="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
          >Prev</button>
          <button
            [disabled]="currentPage >= svc.listings().totalPages - 1"
            (click)="next()"
            class="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-40"
          >Next</button>
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

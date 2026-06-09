import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AiSummaryResponse,
  ListingDetailResponse,
  ListingPage,
  ListingReportResponse,
  ScrapingSessionResponse,
} from './api.types';

@Injectable({ providedIn: 'root' })
export class ListingsService {
  private readonly http = inject(HttpClient);

  readonly listings = signal<ListingPage>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 20,
  });
  readonly currentListing = signal<ListingDetailResponse | null>(null);
  readonly report = signal<ListingReportResponse | null>(null);
  readonly summary = signal<AiSummaryResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadListings(page: number, size: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<ListingPage>(`/api/listings?page=${page}&size=${size}`)
      .subscribe({
        next: data => {
          this.listings.set(data);
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err.error?.detail ?? 'Failed to load listings');
          this.loading.set(false);
        },
      });
  }

  loadListing(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentListing.set(null);
    this.http.get<ListingDetailResponse>(`/api/listings/${id}`).subscribe({
      next: data => {
        this.currentListing.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to load listing');
        this.loading.set(false);
      },
    });
  }

  loadReport(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.report.set(null);
    this.http.get<ListingReportResponse>(`/api/listings/${id}/report`).subscribe({
      next: data => {
        this.report.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to load report');
        this.loading.set(false);
      },
    });
  }

  loadSummary(id: string): void {
    this.summary.set(null);
    this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`).subscribe({
      next: data => this.summary.set(data),
      error: () => this.summary.set(null),
    });
  }

  rescrape(id: string): Observable<ScrapingSessionResponse> {
    return this.http.post<ScrapingSessionResponse>(
      `/api/listings/${id}/rescrape`,
      {}
    );
  }
}

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AiSummaryResponse,
  ListingDetailResponse,
  ListingPage,
  ListingReportResponse,
  ListingSearchFilter,
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
  readonly summaryNotFound = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadListings(page: number, size: number, filter?: ListingSearchFilter): void {
    this.loading.set(true);
    this.error.set(null);
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter?.street) params = params.set('street', filter.street);
    if (filter?.houseNumber) params = params.set('houseNumber', filter.houseNumber);
    if (filter?.houseNumberAddition) params = params.set('houseNumberAddition', filter.houseNumberAddition);
    if (filter?.zipCode) params = params.set('zipCode', filter.zipCode);
    if (filter?.city?.trim()) params = params.set('city', filter.city.trim());
    if (filter?.province) params = params.set('province', filter.province);
    if (filter?.minBedrooms) params = params.set('minBedrooms', filter.minBedrooms);
    if (filter?.minRooms) params = params.set('minRooms', filter.minRooms);
    if (filter?.minLivingAreaM2) params = params.set('minLivingAreaM2', filter.minLivingAreaM2);
    if (filter?.energyLabel?.trim()) params = params.set('energyLabel', filter.energyLabel.trim());
    if (filter?.radiusKm) params = params.set('radiusKm', filter.radiusKm);
    this.http.get<ListingPage>('/api/listings', { params }).subscribe({
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
        this.error.set(err.status === 404 ? '404' : (err.error?.detail ?? 'Failed to load report'));
        this.loading.set(false);
      },
    });
  }

  loadListingAndReport(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentListing.set(null);
    this.report.set(null);
    this.http.get<ListingDetailResponse>(`/api/listings/${id}`).subscribe({
      next: listing => {
        this.currentListing.set(listing);
        this.http.get<ListingReportResponse>(`/api/listings/${id}/report`).subscribe({
          next: report => {
            this.report.set(report);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: err => {
        this.error.set(err.status === 404 ? '404' : (err.error?.detail ?? 'Failed to load listing'));
        this.loading.set(false);
      },
    });
  }

  readonly summaryGenerating = signal(false);
  private summaryPollInterval?: ReturnType<typeof setInterval>;

  loadSummary(id: string): void {
    this.summary.set(null);
    this.summaryNotFound.set(false);
    this.summaryGenerating.set(false);
    this.clearSummaryPoll();
    this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`).subscribe({
      next: data => this.summary.set(data),
      error: () => this.summaryNotFound.set(true),
    });
  }

  requestSummaryGeneration(id: string): void {
    this.summaryGenerating.set(true);
    this.summaryNotFound.set(false);
    this.http.post(`/api/listings/${id}/summary/generate`, {}).subscribe({
      next: () => this.startSummaryPoll(id),
      error: () => this.summaryGenerating.set(false),
    });
  }

  private startSummaryPoll(id: string): void {
    this.clearSummaryPoll();
    this.summaryPollInterval = setInterval(() => {
      this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`).subscribe({
        next: data => {
          this.summary.set(data);
          this.summaryGenerating.set(false);
          this.clearSummaryPoll();
        },
        error: () => {},
      });
    }, 3000);
  }

  clearSummaryPoll(): void {
    if (this.summaryPollInterval !== undefined) {
      clearInterval(this.summaryPollInterval);
      this.summaryPollInterval = undefined;
    }
  }

  rescrape(id: string): Observable<ScrapingSessionResponse> {
    return this.http.post<ScrapingSessionResponse>(`/api/listings/${id}/rescrape`, {});
  }
}

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subscription, catchError, of, switchMap } from 'rxjs';
import {
  AiSummaryResponse,
  ListingDetailResponse,
  ListingPage,
  ListingReportResponse,
  ListingSearchFilter,
  ScrapingSessionResponse,
} from './api.types';
import { pollUntil } from './poll';
import { defaultErrorMessage, runRequest } from './request-state';

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

  private notFoundOr(fallback: string): (err: any) => string {
    return err => (err.status === 404 ? '404' : (err.error?.detail ?? fallback));
  }

  loadListings(page: number, size: number, filter?: ListingSearchFilter): void {
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
    runRequest(
      this.http.get<ListingPage>('/api/listings', { params }),
      this,
      data => this.listings.set(data),
      defaultErrorMessage('Failed to load listings')
    );
  }

  loadReport(id: string): void {
    this.report.set(null);
    runRequest(
      this.http.get<ListingReportResponse>(`/api/listings/${id}/report`),
      this,
      data => this.report.set(data),
      this.notFoundOr('Failed to load report')
    );
  }

  loadListingAndReport(id: string): void {
    this.currentListing.set(null);
    this.report.set(null);
    const listingThenReport$ = this.http.get<ListingDetailResponse>(`/api/listings/${id}`).pipe(
      switchMap(listing => {
        this.currentListing.set(listing);
        return this.http
          .get<ListingReportResponse>(`/api/listings/${id}/report`)
          .pipe(catchError(() => of(null)));
      })
    );
    runRequest(listingThenReport$, this, report => this.report.set(report), this.notFoundOr('Failed to load listing'));
  }

  readonly summaryGenerating = signal(false);
  private summaryPollSub?: Subscription;

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
    this.summaryPollSub = pollUntil(() => this.http.get<AiSummaryResponse>(`/api/listings/${id}/summary`), {
      isTerminal: () => true,
      onNext: data => {
        this.summary.set(data);
        this.summaryGenerating.set(false);
      },
    });
  }

  clearSummaryPoll(): void {
    this.summaryPollSub?.unsubscribe();
    this.summaryPollSub = undefined;
  }

  rescrape(id: string): Observable<ScrapingSessionResponse> {
    return this.http.post<ScrapingSessionResponse>(`/api/listings/${id}/rescrape`, {});
  }
}

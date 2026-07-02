import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  CreateScrapingSessionRequest,
  GeocodingBackfillResponse,
  ScrapingSessionResponse,
  SessionStatus,
  TERMINAL_STATUSES,
} from './api.types';
const MAX_POLL_ERRORS = 3;

@Injectable({ providedIn: 'root' })
export class ScrapingService {
  private readonly http = inject(HttpClient);
  private pollInterval?: ReturnType<typeof setInterval>;
  private consecutivePollErrors = 0;

  readonly session = signal<ScrapingSessionResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly backfillResult = signal<GeocodingBackfillResponse | null>(null);
  readonly backfillLoading = signal(false);
  readonly backfillError = signal<string | null>(null);

  createSession(req: CreateScrapingSessionRequest): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .post<ScrapingSessionResponse>('/api/scraping-sessions', req)
      .subscribe({
        next: data => {
          this.session.set(data);
          this.loading.set(false);
          this.startPolling(data.id);
        },
        error: err => {
          this.error.set(err.error?.detail ?? 'Failed to create scraping session');
          this.loading.set(false);
        },
      });
  }

  pollSession(id: string): void {
    this.http
      .get<ScrapingSessionResponse>(`/api/scraping-sessions/${id}`)
      .subscribe({
        next: data => {
          this.consecutivePollErrors = 0;
          this.session.set(data);
          if (TERMINAL_STATUSES.includes(data.status)) {
            this.stopPolling();
          }
        },
        error: () => {
          this.consecutivePollErrors++;
          if (this.consecutivePollErrors >= MAX_POLL_ERRORS) {
            this.stopPolling();
          }
        },
      });
  }

  private startPolling(id: string): void {
    this.stopPolling();
    this.consecutivePollErrors = 0;
    this.pollInterval = setInterval(() => this.pollSession(id), 3000);
  }

  stopPolling(): void {
    if (this.pollInterval !== undefined) {
      clearInterval(this.pollInterval);
      this.pollInterval = undefined;
    }
  }

  backfillGeocoding(): void {
    this.backfillLoading.set(true);
    this.backfillError.set(null);
    this.backfillResult.set(null);
    this.http.post<GeocodingBackfillResponse>('/api/listings/geocoding/backfill', {}).subscribe({
      next: data => {
        this.backfillResult.set(data);
        this.backfillLoading.set(false);
      },
      error: err => {
        this.backfillError.set(err.error?.detail ?? 'Failed to queue geocoding backfill');
        this.backfillLoading.set(false);
      },
    });
  }
}

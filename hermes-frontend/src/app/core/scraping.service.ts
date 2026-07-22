import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import {
  CreateScrapingSessionRequest,
  GeocodingBackfillResponse,
  ScrapingSessionResponse,
  TERMINAL_STATUSES,
} from './api.types';
import { pollUntil } from './poll';
import { defaultErrorMessage, runRequest } from './request-state';
const MAX_POLL_ERRORS = 3;

@Injectable({ providedIn: 'root' })
export class ScrapingService {
  private readonly http = inject(HttpClient);
  private pollSub?: Subscription;

  readonly session = signal<ScrapingSessionResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly backfillResult = signal<GeocodingBackfillResponse | null>(null);
  readonly backfillLoading = signal(false);
  readonly backfillError = signal<string | null>(null);

  createSession(req: CreateScrapingSessionRequest): void {
    runRequest(
      this.http.post<ScrapingSessionResponse>('/api/scraping-sessions', req),
      this,
      data => {
        this.session.set(data);
        this.startPolling(data.id);
      },
      defaultErrorMessage('Failed to create scraping session')
    );
  }

  private startPolling(id: string): void {
    this.stopPolling();
    this.pollSub = pollUntil(() => this.http.get<ScrapingSessionResponse>(`/api/scraping-sessions/${id}`), {
      maxConsecutiveErrors: MAX_POLL_ERRORS,
      onNext: data => this.session.set(data),
      isTerminal: data => TERMINAL_STATUSES.includes(data.status),
    });
  }

  stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
  }

  backfillGeocoding(): void {
    this.backfillResult.set(null);
    runRequest(
      this.http.post<GeocodingBackfillResponse>('/api/listings/geocoding/backfill', {}),
      { loading: this.backfillLoading, error: this.backfillError },
      data => this.backfillResult.set(data),
      defaultErrorMessage('Failed to queue geocoding backfill')
    );
  }
}

import { WritableSignal } from '@angular/core';
import { Observable } from 'rxjs';

export interface RequestStateSignals {
  loading: WritableSignal<boolean>;
  error: WritableSignal<string | null>;
}

/** Builds an error mapper that reads `error.error.detail`, falling back to a fixed message. */
export function defaultErrorMessage(fallback: string): (err: any) => string {
  return err => err?.error?.detail ?? fallback;
}

/**
 * Runs a request while driving a loading/error signal pair: sets loading and
 * clears error before subscribing, calls onSuccess with the result, and
 * always clears loading once the request settles.
 */
export function runRequest<T>(
  request: Observable<T>,
  state: RequestStateSignals,
  onSuccess: (data: T) => void,
  toErrorMessage: (err: any) => string
): void {
  state.loading.set(true);
  state.error.set(null);
  request.subscribe({
    next: data => {
      onSuccess(data);
      state.loading.set(false);
    },
    error: err => {
      state.error.set(toErrorMessage(err));
      state.loading.set(false);
    },
  });
}

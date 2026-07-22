import { Observable, of, Subscription, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

export interface PollUntilOptions<T> {
  /** Delay between polls, and before the first poll. Defaults to 3000ms. */
  intervalMs?: number;
  /** Give up after this many polls fail in a row. Defaults to unlimited. */
  maxConsecutiveErrors?: number;
  /** Called with each successful poll result. */
  onNext: (value: T) => void;
  /** Polling stops (successfully) once this returns true for a result. */
  isTerminal: (value: T) => boolean;
  /** Called after each failed poll, with the current consecutive-error count. */
  onError?: (consecutiveErrors: number) => void;
}

type PollResult<T> = { ok: true; value: T } | { ok: false };

/**
 * Repeatedly calls `fetch` on an interval until `isTerminal` matches a result
 * or `maxConsecutiveErrors` is reached. A failed poll does not stop the
 * interval by itself - only consecutive failures past the limit do.
 *
 * Call `.unsubscribe()` on the returned Subscription to cancel polling early.
 */
export function pollUntil<T>(fetch: () => Observable<T>, options: PollUntilOptions<T>): Subscription {
  const intervalMs = options.intervalMs ?? 3000;
  const maxConsecutiveErrors = options.maxConsecutiveErrors ?? Infinity;
  let consecutiveErrors = 0;

  const subscription = timer(intervalMs, intervalMs)
    .pipe(
      switchMap(() =>
        fetch().pipe(
          map((value): PollResult<T> => ({ ok: true, value })),
          catchError(() => of<PollResult<T>>({ ok: false }))
        )
      )
    )
    .subscribe(result => {
      if (result.ok) {
        consecutiveErrors = 0;
        options.onNext(result.value);
        if (options.isTerminal(result.value)) {
          subscription.unsubscribe();
        }
      } else {
        consecutiveErrors++;
        options.onError?.(consecutiveErrors);
        if (consecutiveErrors >= maxConsecutiveErrors) {
          subscription.unsubscribe();
        }
      }
    });

  return subscription;
}

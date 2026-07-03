import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AddressResponse, UpdateAddressRequest } from './api.types';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  readonly address = signal<AddressResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadProfile(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<AddressResponse>('/api/profile').subscribe({
      next: data => {
        this.address.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to load profile');
        this.loading.set(false);
      },
    });
  }

  updateAddress(req: UpdateAddressRequest): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.put<AddressResponse>('/api/profile/address', req).subscribe({
      next: data => {
        this.address.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Failed to save address');
        this.loading.set(false);
      },
    });
  }
}

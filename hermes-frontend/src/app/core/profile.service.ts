import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AddressResponse, UpdateAddressRequest } from './api.types';
import { defaultErrorMessage, runRequest } from './request-state';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  readonly address = signal<AddressResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  loadProfile(): void {
    runRequest(
      this.http.get<AddressResponse>('/api/profile'),
      this,
      data => this.address.set(data),
      defaultErrorMessage('Failed to load profile')
    );
  }

  updateAddress(req: UpdateAddressRequest): void {
    runRequest(
      this.http.put<AddressResponse>('/api/profile/address', req),
      this,
      data => this.address.set(data),
      defaultErrorMessage('Failed to save address')
    );
  }
}

import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfileService } from './profile.service';
import { AddressResponse } from './api.types';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loadProfile sets address on success', () => {
    const response: AddressResponse = { street: 'Dorpstraat', city: 'Utrecht' };

    service.loadProfile();
    const req = httpMock.expectOne('/api/profile');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.address()).toEqual(response);
    expect(service.loading()).toBe(false);
  });

  it('updateAddress sets error message on 422', () => {
    service.updateAddress({ street: 'X', houseNumber: '1', city: 'Nowhere' });
    const req = httpMock.expectOne('/api/profile/address');
    expect(req.request.method).toBe('PUT');
    req.flush(
      { detail: 'Address could not be geocoded' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(service.error()).toBe('Address could not be geocoded');
    expect(service.loading()).toBe(false);
  });
});

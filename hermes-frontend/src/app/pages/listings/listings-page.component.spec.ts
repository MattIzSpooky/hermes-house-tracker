import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ListingsPageComponent } from './listings-page.component';
import { ListingsService } from '../../core/listings.service';

describe('ListingsPageComponent', () => {
  let fixture: ComponentFixture<ListingsPageComponent>;
  let component: ListingsPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListingsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ListingsPageComponent);
    component = fixture.componentInstance;
  });

  it('defaults to list view', () => {
    expect((component as any).viewMode).toBe('list');
  });

  it('toggleView switches to map and back', () => {
    (component as any).toggleView('map');
    expect((component as any).viewMode).toBe('map');

    (component as any).toggleView('list');
    expect((component as any).viewMode).toBe('list');
  });

  it('mapListings maps the current page content with location passed through', () => {
    const svc = TestBed.inject(ListingsService);
    svc.listings.set({
      content: [
        { id: '1', street: 'Kerkstraat', houseNumber: '5', zipCode: '1000AA', city: 'Amsterdam', province: 'Noord-Holland', askingPrice: 350000, firstSeenAt: '2026-01-01', location: { latitude: 52.1, longitude: 4.9 } },
      ],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    });
    fixture.detectChanges();

    const mapped = (component as any).mapListings();

    expect(mapped).toEqual([
      { id: '1', street: 'Kerkstraat', houseNumber: '5', city: 'Amsterdam', currentPrice: 350000, location: { latitude: 52.1, longitude: 4.9 } },
    ]);
  });
});

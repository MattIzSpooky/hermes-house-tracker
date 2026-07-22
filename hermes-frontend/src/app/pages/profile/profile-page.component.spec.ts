import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('patches form fields from the loaded profile', () => {
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/profile');
    req.flush({ street: 'Dorpstraat', houseNumber: '10', city: 'Utrecht' });
    fixture.detectChanges();

    expect((fixture.componentInstance as any).form.street).toBe('Dorpstraat');
    expect((fixture.componentInstance as any).form.houseNumber).toBe('10');
    expect((fixture.componentInstance as any).form.city).toBe('Utrecht');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ErrorAlertComponent } from './error-alert.component';

describe('ErrorAlertComponent', () => {
  let fixture: ComponentFixture<ErrorAlertComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorAlertComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(ErrorAlertComponent);
    fixture.componentRef.setInput('message', 'Something went wrong');
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should display the error message', async () => {
    expect(el.textContent?.trim()).toContain('Something went wrong');
  });

  it('should have red error styling', async () => {
    expect(el.querySelector('.bg-red-50')).toBeTruthy();
    expect(el.querySelector('.text-red-700')).toBeTruthy();
  });
});

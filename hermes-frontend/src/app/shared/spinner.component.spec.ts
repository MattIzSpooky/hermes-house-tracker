import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SpinnerComponent } from './spinner.component';

describe('SpinnerComponent', () => {
  let fixture: ComponentFixture<SpinnerComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpinnerComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(SpinnerComponent);
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should render the animated spinner element', async () => {
    expect(el.querySelector('.animate-spin')).toBeTruthy();
  });

  it('should use cyan border by default', async () => {
    expect(el.querySelector('.border-cyan-500')).toBeTruthy();
  });

  it('should use white border when color is white', async () => {
    fixture.componentRef.setInput('color', 'white');
    await fixture.whenStable();
    expect(el.querySelector('.border-white')).toBeTruthy();
    expect(el.querySelector('.border-cyan-500')).toBeNull();
  });

  it('should show label text when provided', async () => {
    fixture.componentRef.setInput('label', 'Loading...');
    await fixture.whenStable();
    expect(el.textContent?.trim()).toContain('Loading...');
  });

  it('should show no label text by default', async () => {
    expect(el.textContent?.trim()).toBe('');
  });
});

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StatCardComponent } from './stat-card.component';

@Component({
  standalone: true,
  imports: [StatCardComponent],
  template: `<app-stat-card label="Days in Hermes"><span class="value">42</span></app-stat-card>`,
})
class TestHostComponent {}

describe('StatCardComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(TestHostComponent);
    await fixture.whenStable();
    el = fixture.nativeElement;
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should display the label', async () => {
    expect(el.textContent).toContain('Days in Hermes');
  });

  it('should project content into the card', async () => {
    const projected = el.querySelector('.value');
    expect(projected).toBeTruthy();
    expect(projected?.textContent?.trim()).toBe('42');
  });

  it('should render the card container', async () => {
    expect(el.querySelector('.bg-white.rounded-xl')).toBeTruthy();
  });
});

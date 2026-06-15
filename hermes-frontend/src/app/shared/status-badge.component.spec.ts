import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  async function create(status: string): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const fixture: ComponentFixture<StatusBadgeComponent> = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', status);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('span')!;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('applies emerald class for COMPLETED', async () => {
    const el = await create('COMPLETED');
    expect(el.className).toContain('text-emerald-700');
  });

  it('applies red class for FAILED', async () => {
    const el = await create('FAILED');
    expect(el.className).toContain('text-red-700');
  });

  it('applies cyan class for IN_PROGRESS', async () => {
    const el = await create('IN_PROGRESS');
    expect(el.className).toContain('text-cyan-700');
  });

  it('applies slate class for unknown status', async () => {
    const el = await create('UNKNOWN_XYZ');
    expect(el.className).toContain('text-slate-600');
  });
});

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SectionCardComponent } from './section-card.component';

@Component({
  standalone: true,
  imports: [SectionCardComponent],
  template: `<app-section-card><p class="content">Hello</p></app-section-card>`,
})
class TestHostComponent {}

describe('SectionCardComponent', () => {
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

  it('should render the card shell', async () => {
    expect(el.querySelector('.bg-white.rounded-xl')).toBeTruthy();
  });

  it('should apply default p-5 padding', async () => {
    expect(el.querySelector('.p-5')).toBeTruthy();
  });

  it('should project content', async () => {
    expect(el.querySelector('.content')).toBeTruthy();
  });
});

describe('SectionCardComponent — inputs', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('should apply custom padding when provided', async () => {
    @Component({
      standalone: true,
      imports: [SectionCardComponent],
      template: `<app-section-card padding="p-6"><p>X</p></app-section-card>`,
    })
    class Host {}
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const f = TestBed.createComponent(Host);
    await f.whenStable();
    expect(f.nativeElement.querySelector('.p-6')).toBeTruthy();
  });

  it('should apply extraClass to the inner card div', async () => {
    @Component({
      standalone: true,
      imports: [SectionCardComponent],
      template: `<app-section-card extraClass="space-y-4"><p>X</p></app-section-card>`,
    })
    class Host {}
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const f = TestBed.createComponent(Host);
    await f.whenStable();
    expect(f.nativeElement.querySelector('.space-y-4')).toBeTruthy();
  });

  it('should render base classes when padding is empty string', async () => {
    @Component({
      standalone: true,
      imports: [SectionCardComponent],
      template: `<app-section-card padding=""><p>X</p></app-section-card>`,
    })
    class Host {}
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    const f = TestBed.createComponent(Host);
    await f.whenStable();
    const card = f.nativeElement.querySelector('.bg-white.rounded-xl');
    expect(card).toBeTruthy();
    expect(card?.className).not.toContain('  '); // no double spaces
  });
});

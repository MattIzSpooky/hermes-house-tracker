import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ListingMapComponent, MapListing } from './listing-map.component';

describe('ListingMapComponent', () => {
  let fixture: ComponentFixture<ListingMapComponent>;
  let component: ListingMapComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListingMapComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ListingMapComponent);
    component = fixture.componentInstance;
  });

  const withLocation: MapListing = {
    id: 'a1',
    street: 'Kerkstraat',
    houseNumber: '5',
    city: 'Amsterdam',
    currentPrice: 350000,
    location: { latitude: 52.3676, longitude: 4.9041, bboxLatMin: 52.3666, bboxLatMax: 52.3686, bboxLonMin: 4.9031, bboxLonMax: 4.9051 },
  };

  const withoutLocation: MapListing = {
    id: 'b2',
    street: 'Damrak',
    houseNumber: '1',
    city: 'Amsterdam',
    currentPrice: 400000,
    location: null,
  };

  it('creates without error when given no listings', () => {
    fixture.componentRef.setInput('listings', []);
    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('only plots listings that have a location', () => {
    fixture.componentRef.setInput('listings', [withLocation, withoutLocation]);
    fixture.detectChanges();

    expect(component.plottedCount()).toBe(1);
  });

  it('emits listingSelected with the correct id when a marker is clicked', () => {
    fixture.componentRef.setInput('listings', [withLocation]);
    fixture.detectChanges();

    const emitted: string[] = [];
    component.listingSelected.subscribe(id => emitted.push(id));

    component.selectListing(withLocation.id);

    expect(emitted).toEqual(['a1']);
  });

  it('recomputes plotted markers when the listings input changes', () => {
    fixture.componentRef.setInput('listings', [withoutLocation]);
    fixture.detectChanges();
    expect(component.plottedCount()).toBe(0);

    fixture.componentRef.setInput('listings', [withLocation, withoutLocation]);
    fixture.detectChanges();
    expect(component.plottedCount()).toBe(1);
  });

  it('escapes HTML-special characters from scraped address fields in the tooltip text', () => {
    const maliciousListing: MapListing = {
      id: 'c3',
      street: '<script>alert(1)</script>',
      houseNumber: '"5" & <b>',
      city: "O'Brien",
      currentPrice: 100000,
      location: { latitude: 52.0, longitude: 5.0 },
    };

    const tooltip = (component as any).tooltipText(maliciousListing) as string;

    expect(tooltip).not.toContain('<script>');
    expect(tooltip).not.toContain('<b>');
    expect(tooltip).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(tooltip).toContain('&quot;5&quot; &amp; &lt;b&gt;');
    expect(tooltip).toContain('O&#39;Brien');
  });
});

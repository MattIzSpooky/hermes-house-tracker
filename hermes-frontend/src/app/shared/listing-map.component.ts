import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import * as L from 'leaflet';
import { GeoLocation } from '../core/api.types';

// Angular's build pipeline does not resolve Leaflet's default marker icon
// paths correctly; point them at the bundled asset URLs explicitly.
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});

export interface MapListing {
  id: string;
  street?: string;
  houseNumber?: string;
  city?: string;
  currentPrice?: number;
  location?: GeoLocation | null;
}

@Component({
  selector: 'app-listing-map',
  standalone: true,
  template: `<div #mapContainer class="w-full h-full min-h-[320px] rounded-xl"></div>`,
})
export class ListingMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() listings: MapListing[] = [];
  @Output() listingSelected = new EventEmitter<string>();

  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private layerGroup?: L.LayerGroup;
  private viewReady = false;

  private readonly listingsSignal = signal<MapListing[]>([]);
  readonly plottedCount = computed(
    () => this.listingsSignal().filter(l => this.hasLocation(l)).length,
  );

  ngAfterViewInit(): void {
    this.map = L.map(this.mapContainer.nativeElement).setView([52.1326, 5.2913], 7); // Netherlands default
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);
    this.layerGroup = L.layerGroup().addTo(this.map);
    this.viewReady = true;
    this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['listings']) {
      this.listingsSignal.set(this.listings ?? []);
      if (this.viewReady) this.render();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private hasLocation(l: MapListing): l is MapListing & { location: GeoLocation } {
    return l.location != null;
  }

  private render(): void {
    if (!this.layerGroup || !this.map) return;
    this.layerGroup.clearLayers();

    const plotted = this.listingsSignal().filter(l => this.hasLocation(l));
    const bounds: L.LatLngBoundsExpression = [];

    for (const listing of plotted) {
      const loc = listing.location!;
      const marker = L.marker([loc.latitude, loc.longitude]);
      marker.bindTooltip(this.tooltipText(listing));
      marker.on('click', () => this.selectListing(listing.id));
      marker.addTo(this.layerGroup!);
      bounds.push([loc.latitude, loc.longitude]);

      if (loc.bboxLatMin != null && loc.bboxLatMax != null && loc.bboxLonMin != null && loc.bboxLonMax != null) {
        const rectBounds: L.LatLngBoundsExpression = [
          [loc.bboxLatMin, loc.bboxLonMin],
          [loc.bboxLatMax, loc.bboxLonMax],
        ];
        const rect = L.rectangle(rectBounds, { color: '#06b6d4', weight: 1, fillOpacity: 0.1 });
        rect.on('click', () => this.selectListing(listing.id));
        rect.addTo(this.layerGroup!);
        bounds.push([loc.bboxLatMin, loc.bboxLonMin], [loc.bboxLatMax, loc.bboxLonMax]);
      }
    }

    if (bounds.length > 0) {
      this.map.fitBounds(bounds, { padding: [24, 24] });
    }
  }

  private tooltipText(listing: MapListing): string {
    const address = [listing.street, listing.houseNumber].filter(Boolean).join(' ');
    const price = listing.currentPrice != null ? `€ ${listing.currentPrice.toLocaleString('nl-NL')}` : '';
    return [address, listing.city, price].filter(Boolean).join(' · ');
  }

  selectListing(id: string): void {
    this.listingSelected.emit(id);
  }
}

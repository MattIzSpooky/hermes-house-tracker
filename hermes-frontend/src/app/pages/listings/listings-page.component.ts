import { Component, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { ListingsService } from '../../core/listings.service';
import { FavoritesService } from '../../core/favorites.service';
import { ListingSearchFilter } from '../../core/api.types';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EuroPricePipe } from '../../shared/euro-price.pipe';
import { SpinnerComponent } from '../../shared/spinner.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';
import { ListingMapComponent, MapListing } from '../../shared/listing-map.component';

@Component({
  selector: 'app-listings-page',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, EuroPricePipe, SpinnerComponent, ErrorAlertComponent, SectionCardComponent, ListingMapComponent],
  templateUrl: './listings-page.component.html',
})

export class ListingsPageComponent implements OnInit, OnDestroy {
  protected readonly svc = inject(ListingsService);
  protected readonly favorites = inject(FavoritesService);
  private readonly router = inject(Router);

  protected currentPage = 0;
  protected pageSize = 20;

  protected viewMode: 'list' | 'map' = 'list';

  protected readonly mapListings = computed<MapListing[]>(() =>
    this.svc.listings().content.map(l => ({
      id: l.id,
      street: l.street,
      houseNumber: l.houseNumber,
      city: l.city,
      currentPrice: l.askingPrice,
      location: l.location,
    })),
  );

  protected readonly listingsWithoutLocationCount = computed(
    () => this.mapListings().filter(l => l.location == null).length,
  );

  toggleView(mode: 'list' | 'map'): void {
    this.viewMode = mode;
  }

  protected filter: ListingSearchFilter = {};

  private readonly filterChange$ = new Subject<void>();
  private filterSub?: Subscription;

  ngOnInit(): void {
    this.svc.loadListings(this.currentPage, this.pageSize);
    this.filterSub = this.filterChange$.pipe(debounceTime(300)).subscribe(() => {
      this.currentPage = 0;
      this.loadWithFilters();
    });
  }

  ngOnDestroy(): void {
    this.filterSub?.unsubscribe();
  }

  onFilterChange(): void {
    this.filterChange$.next();
  }

  clearFilters(): void {
    this.filter = {};
    this.currentPage = 0;
    this.svc.loadListings(0, this.pageSize);
  }

  onPageSizeChange(): void {
    this.currentPage = 0;
    this.loadWithFilters();
  }

  prev(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadWithFilters();
    }
  }

  next(): void {
    this.currentPage++;
    this.loadWithFilters();
  }

  navigate(id: string): void {
    this.router.navigate(['/listings', id]);
  }

  private loadWithFilters(): void {
    this.svc.loadListings(this.currentPage, this.pageSize, this.filter);
  }
}

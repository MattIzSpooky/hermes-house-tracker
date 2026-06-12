import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/listings', pathMatch: 'full' },
  {
    path: 'listings',
    loadComponent: () =>
      import('./pages/listings/listings-page.component').then(
        m => m.ListingsPageComponent
      ),
  },
  {
    path: 'listings/:id',
    loadComponent: () =>
      import('./pages/listing-detail/listing-detail-page.component').then(
        m => m.ListingDetailPageComponent
      ),
  },
  {
    path: 'scraping',
    loadComponent: () =>
      import('./pages/scraping/scraping-page.component').then(
        m => m.ScrapingPageComponent
      ),
  },
];

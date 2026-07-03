import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/listings', pathMatch: 'full' },
  {
    path: 'listings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/listings/listings-page.component').then(
        m => m.ListingsPageComponent
      ),
  },
  {
    path: 'listings/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/listing-detail/listing-detail-page.component').then(
        m => m.ListingDetailPageComponent
      ),
  },
  {
    path: 'scraping',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/scraping/scraping-page.component').then(
        m => m.ScrapingPageComponent
      ),
  },
  {
    path: 'watches',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/watches/watches-page.component').then(
        m => m.WatchesPageComponent
      ),
  },
];

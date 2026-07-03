import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { adminGuard } from './core/admin.guard';

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
    canActivate: [adminGuard],
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
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/profile/profile-page.component').then(
        m => m.ProfilePageComponent
      ),
  },
];

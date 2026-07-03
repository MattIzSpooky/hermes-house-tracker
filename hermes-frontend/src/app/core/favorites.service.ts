import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FavoriteDto } from './api.types';

@Injectable({ providedIn: 'root' })
export class FavoritesService {
  private readonly http = inject(HttpClient);

  private readonly _favorites = signal<Set<string>>(new Set());
  readonly favoriteIds = this._favorites.asReadonly();

  constructor() {
    this.loadFavorites();
  }

  isFavorite(listingId: string): boolean {
    return this._favorites().has(listingId);
  }

  toggle(listingId: string): void {
    if (this.isFavorite(listingId)) {
      this.remove(listingId);
    } else {
      this.add(listingId);
    }
  }

  private add(listingId: string): void {
    this._favorites.update(set => new Set([...set, listingId]));
    this.http.put(`/api/favorites/${listingId}`, {}).subscribe({
      error: () => this._favorites.update(set => { const s = new Set(set); s.delete(listingId); return s; }),
    });
  }

  private remove(listingId: string): void {
    this._favorites.update(set => { const s = new Set(set); s.delete(listingId); return s; });
    this.http.delete(`/api/favorites/${listingId}`).subscribe({
      error: () => this._favorites.update(set => new Set([...set, listingId])),
    });
  }

  private loadFavorites(): void {
    this.http.get<FavoriteDto[]>(`/api/favorites`).subscribe({
      next: dtos => this._favorites.set(new Set(dtos.map(d => d.listingId))),
    });
  }
}

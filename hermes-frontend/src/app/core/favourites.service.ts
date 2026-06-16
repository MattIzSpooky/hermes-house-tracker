import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FavouriteDto } from './api.types';

@Injectable({ providedIn: 'root' })
export class FavouritesService {
  private readonly http = inject(HttpClient);

  readonly clientId: string;

  private readonly _favourites = signal<Set<string>>(new Set());
  readonly favouriteIds = this._favourites.asReadonly();

  constructor() {
    this.clientId = localStorage.getItem('hermes-chat-session') ?? this.initClientId();
    this.loadFavourites();
  }

  private initClientId(): string {
    const id = crypto.randomUUID();
    localStorage.setItem('hermes-chat-session', id);
    return id;
  }

  isFavourite(listingId: string): boolean {
    return this._favourites().has(listingId);
  }

  toggle(listingId: string): void {
    if (this.isFavourite(listingId)) {
      this.remove(listingId);
    } else {
      this.add(listingId);
    }
  }

  private add(listingId: string): void {
    this._favourites.update(set => new Set([...set, listingId]));
    this.http.put(`/api/favourites/${this.clientId}/${listingId}`, {}).subscribe({
      error: () => this._favourites.update(set => { const s = new Set(set); s.delete(listingId); return s; }),
    });
  }

  private remove(listingId: string): void {
    this._favourites.update(set => { const s = new Set(set); s.delete(listingId); return s; });
    this.http.delete(`/api/favourites/${this.clientId}/${listingId}`).subscribe({
      error: () => this._favourites.update(set => new Set([...set, listingId])),
    });
  }

  private loadFavourites(): void {
    this.http.get<FavouriteDto[]>(`/api/favourites/${this.clientId}`).subscribe({
      next: dtos => this._favourites.set(new Set(dtos.map(d => d.listingId))),
    });
  }
}

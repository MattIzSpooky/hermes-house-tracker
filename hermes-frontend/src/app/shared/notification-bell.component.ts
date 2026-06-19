import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NotificationsService } from '../core/notifications.service';
import { ChatService } from '../core/chat.service';
import { NotificationResponse, ListingDetailResponse } from '../core/api.types';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TitleCasePipe, RouterLink],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  protected readonly svc = inject(NotificationsService);
  protected readonly chat = inject(ChatService);
  private readonly http = inject(HttpClient);

  protected panelOpen = signal(false);
  protected selected = signal<NotificationResponse | null>(null);
  protected listingDetails = signal<ListingDetailResponse[]>([]);

  protected toggle(): void {
    this.panelOpen.update(o => !o);
    if (!this.panelOpen()) this.selected.set(null);
  }

  protected open(n: NotificationResponse): void {
    this.selected.set(n);
    this.listingDetails.set([]);
    if (!n.read) this.svc.markRead(n.id);
    if (n.listingIds?.length) this.fetchListings(n.listingIds);
  }

  protected back(): void {
    this.selected.set(null);
    this.listingDetails.set([]);
  }

  protected continueInChat(n: NotificationResponse): void {
    this.chat.seedAndOpen(n.body);
    this.panelOpen.set(false);
    this.selected.set(null);
  }

  private fetchListings(ids: string[]): void {
    ids.forEach(id => {
      this.http.get<ListingDetailResponse>(`/api/listings/${id}`)
        .pipe(catchError(() => of(null)))
        .subscribe(detail => {
          if (detail) this.listingDetails.update(prev => [...prev, detail]);
        });
    });
  }
}

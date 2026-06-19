import { Component, inject, signal } from '@angular/core';
import { NotificationsService } from '../core/notifications.service';
import { NotificationResponse } from '../core/api.types';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  protected readonly svc = inject(NotificationsService);
  protected panelOpen = signal(false);
  protected selected = signal<NotificationResponse | null>(null);

  protected toggle(): void {
    this.panelOpen.update(o => !o);
    if (!this.panelOpen()) this.selected.set(null);
  }

  protected open(n: NotificationResponse): void {
    this.selected.set(n);
    if (!n.read) this.svc.markRead(n.id);
  }

  protected back(): void {
    this.selected.set(null);
  }
}

import { Component, inject, signal } from '@angular/core';
import { NotificationsService } from '../core/notifications.service';
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

  protected toggle(): void {
    this.panelOpen.update(o => !o);
  }

  protected markRead(id: string, event: Event): void {
    event.stopPropagation();
    this.svc.markRead(id);
  }
}

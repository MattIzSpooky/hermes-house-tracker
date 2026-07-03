import { Injectable, signal, computed, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { catchError, of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { NotificationResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class NotificationsService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly keycloak = inject(Keycloak);

  private readonly stompClient: Client;
  private subscription?: StompSubscription;

  private readonly _notifications = signal<NotificationResponse[]>([]);
  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = computed(() => this._notifications().filter(n => !n.read).length);

  constructor() {
    this.stompClient = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      reconnectDelay: 5000,
      beforeConnect: async () => {
        await this.keycloak.updateToken(30);
        this.stompClient.connectHeaders = { Authorization: `Bearer ${this.keycloak.token}` };
      },
      onConnect: () => this.subscribeAndLoad(),
    });
    this.stompClient.activate();
  }

  ngOnDestroy(): void {
    this.stompClient.deactivate();
  }

  private subscribeAndLoad(): void {
    this.loadNotifications();
    this.subscription?.unsubscribe();
    this.subscription = this.stompClient.subscribe(
      '/user/queue/notifications',
      (msg: IMessage) => {
        const incoming = JSON.parse(msg.body) as NotificationResponse;
        this._notifications.update(prev => [incoming, ...prev]);
      }
    );
  }

  private loadNotifications(): void {
    this.http.get<NotificationResponse[]>('/api/notifications')
      .pipe(
        catchError(() => of([]))
      )
      .subscribe(items => this._notifications.set(items));
  }

  markRead(id: string): void {
    this.http.patch(`/api/notifications/${id}/read`, {})
      .pipe(
        catchError(() => of(null))
      )
      .subscribe(() => {
        this._notifications.update(prev =>
          prev.map(n => n.id === id ? { ...n, read: true } : n)
        );
      });
  }

}

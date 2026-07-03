import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import Keycloak from 'keycloak-js';
import { ChatBubbleComponent } from './shared/chat-bubble.component';
import { NotificationBellComponent } from './shared/notification-bell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ChatBubbleComponent, NotificationBellComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly keycloak = inject(Keycloak);

  get username(): string | undefined {
    return this.keycloak.tokenParsed?.['preferred_username'];
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}

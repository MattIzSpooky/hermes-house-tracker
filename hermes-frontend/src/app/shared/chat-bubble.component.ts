import { Component, inject } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { ChatService } from '../core/chat.service';
import { ChatPanelComponent } from './chat-panel.component';

@Component({
  selector: 'app-chat-bubble',
  standalone: true,
  imports: [ChatPanelComponent],
  templateUrl: './chat-bubble.component.html',
  animations: [
    trigger('slideUp', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(20px) scale(0.95)', transformOrigin: 'bottom right' }),
        animate('220ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0) scale(1)' })),
      ]),
      transition(':leave', [
        animate('160ms ease-in', style({ opacity: 0, transform: 'translateY(12px) scale(0.95)' })),
      ]),
    ]),
  ],
})
export class ChatBubbleComponent {
  protected readonly svc = inject(ChatService);
}

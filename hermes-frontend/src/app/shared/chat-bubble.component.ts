import { Component, inject } from '@angular/core';
import { ChatService } from '../core/chat.service';
import { ChatPanelComponent } from './chat-panel.component';

@Component({
  selector: 'app-chat-bubble',
  standalone: true,
  imports: [ChatPanelComponent],
  templateUrl: './chat-bubble.component.html',
})
export class ChatBubbleComponent {
  protected readonly svc = inject(ChatService);
}

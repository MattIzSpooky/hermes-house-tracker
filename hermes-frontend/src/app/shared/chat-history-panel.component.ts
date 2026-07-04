import { Component, OnInit, inject } from '@angular/core';
import { ChatService } from '../core/chat.service';

@Component({
  selector: 'app-chat-history-panel',
  standalone: true,
  templateUrl: './chat-history-panel.component.html',
})
export class ChatHistoryPanelComponent implements OnInit {
  protected readonly svc = inject(ChatService);

  ngOnInit(): void {
    this.svc.loadSessions();
  }

  protected switchTo(sessionId: string): void {
    this.svc.switchSession(sessionId);
  }

  protected deleteConversation(sessionId: string, event: Event): void {
    event.stopPropagation();
    this.svc.deleteSession(sessionId);
  }

  protected newChat(): void {
    this.svc.startNewChat();
  }
}

import { Component, ElementRef, ViewChild, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EuroPricePipe } from './euro-price.pipe';
import { ChatHistoryPanelComponent } from './chat-history-panel.component';
import { ChatService } from '../core/chat.service';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, EuroPricePipe, ChatHistoryPanelComponent],
  templateUrl: './chat-panel.component.html',
})
export class ChatPanelComponent {
  protected readonly svc = inject(ChatService);
  protected inputText = '';
  protected showHistory = false;

  @ViewChild('messageContainer') private messageContainer!: ElementRef<HTMLElement>;

  constructor() {
    effect(() => {
      this.svc.messages();
      queueMicrotask(() => {
        const el = this.messageContainer?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    });
  }

  protected send(): void {
    this.svc.sendMessage(this.inputText);
    this.inputText = '';
  }

  protected toggleHistory(): void {
    this.showHistory = !this.showHistory;
  }
}

import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SectionCardComponent } from './section-card.component';
import { StatCardComponent } from './stat-card.component';
import { EuroPricePipe } from './euro-price.pipe';
import { ChatService } from '../core/chat.service';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, SectionCardComponent, StatCardComponent, EuroPricePipe],
  templateUrl: './chat-panel.component.html',
})
export class ChatPanelComponent {
  protected readonly svc = inject(ChatService);
  protected inputText = '';

  protected send(): void {
    this.svc.sendMessage(this.inputText);
    this.inputText = '';
  }
}

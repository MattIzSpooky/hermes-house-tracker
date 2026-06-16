import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ChatListingCard, ResultFrame, TokenFrame } from './api.types';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  listings?: ChatListingCard[];
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  readonly sessionId: string;

  private readonly client: Client;
  private subscription?: StompSubscription;
  private readonly _messages = signal<ChatMessage[]>([]);
  private readonly _isStreaming = signal(false);
  private readonly _isOpen = signal(false);

  readonly messages = this._messages.asReadonly();
  readonly isStreaming = this._isStreaming.asReadonly();
  readonly isOpen = this._isOpen.asReadonly();

  constructor() {
    this.sessionId = localStorage.getItem('hermes-chat-session') ?? this.initSession();

    this.client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      reconnectDelay: 5000,
      onConnect: () => this.subscribe(),
    });

    this.client.activate();
  }

  private initSession(): string {
    const id = crypto.randomUUID();
    localStorage.setItem('hermes-chat-session', id);
    return id;
  }

  private subscribe(): void {
    this.subscription?.unsubscribe();
    this.subscription = this.client.subscribe(`/topic/chat/${this.sessionId}`, (msg: IMessage) => {
      const frame = JSON.parse(msg.body) as TokenFrame | ResultFrame;

      if (frame.type === 'TOKEN') {
        this._messages.update(msgs => {
          const last = msgs.at(-1);
          if (last?.role === 'assistant') {
            return [...msgs.slice(0, -1), { ...last, content: last.content + frame.content }];
          }
          return [...msgs, { role: 'assistant', content: frame.content }];
        });
      } else if (frame.type === 'ERROR') {
        this._isStreaming.set(false);
        this._messages.update(msgs => {
          const last = msgs.at(-1);
          if (last?.role === 'assistant') {
            return [...msgs.slice(0, -1), { ...last, content: frame.content }];
          }
          return [...msgs, { role: 'assistant', content: frame.content }];
        });
      } else if (frame.type === 'RESULT') {
        this._isStreaming.set(false);
        if (frame.listings.length > 0) {
          this._messages.update(msgs => {
            const last = msgs.at(-1);
            if (last?.role === 'assistant') {
              return [...msgs.slice(0, -1), { ...last, listings: frame.listings }];
            }
            return msgs;
          });
        }
      }
    });
  }

  sendMessage(text: string): void {
    if (!text.trim() || this._isStreaming() || !this.client.connected) return;
    this._messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this._isStreaming.set(true);
    this.client.publish({
      destination: '/app/chat',
      body: JSON.stringify({ sessionId: this.sessionId, clientId: this.sessionId, message: text }),
    });
  }

  toggle(): void {
    this._isOpen.update(open => !open);
  }
}

import { Injectable, inject, signal, effect } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { catchError, of } from 'rxjs';
import Keycloak from 'keycloak-js';
import { ChatListingCard, ChatMessageResponse, ChatSessionSummaryResponse, ResultFrame, TokenFrame } from './api.types';
import { createAuthenticatedStompClient } from './stomp-client';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  listings?: ChatListingCard[];
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly keycloak = inject(Keycloak);
  private readonly http = inject(HttpClient);

  private _sessionId: string;
  get sessionId(): string {
    return this._sessionId;
  }

  private readonly client: Client;
  private subscription?: StompSubscription;
  private readonly _messages = signal<ChatMessage[]>([]);
  private readonly _isStreaming = signal(false);
  private readonly _isOpen = signal(false);
  private readonly _sessions = signal<ChatSessionSummaryResponse[]>([]);
  private isFreshConversation = true;

  readonly messages = this._messages.asReadonly();
  readonly isStreaming = this._isStreaming.asReadonly();
  readonly isOpen = this._isOpen.asReadonly();
  readonly sessions = this._sessions.asReadonly();

  constructor() {
    this._sessionId = localStorage.getItem('hermes-chat-session') ?? this.generateSessionId();

    this.client = createAuthenticatedStompClient(this.keycloak, () => this.subscribe());
    this.client.activate();

    effect(() => {
      const streaming = this._isStreaming();
      if (!streaming && this.isFreshConversation && this._messages().length > 0) {
        this.isFreshConversation = false;
        this.loadSessions();
      }
    });
  }

  private generateSessionId(): string {
    const id = crypto.randomUUID();
    localStorage.setItem('hermes-chat-session', id);
    return id;
  }

  private subscribe(): void {
    this.subscription?.unsubscribe();
    this.subscription = this.client.subscribe(`/topic/chat/${this._sessionId}`, (msg: IMessage) => {
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
            // Tool was called but no text tokens were emitted; create a message to hold the cards
            return [...msgs, { role: 'assistant', content: '', listings: frame.listings }];
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
      body: JSON.stringify({ sessionId: this._sessionId, message: text }),
    });
  }

  toggle(): void {
    this._isOpen.update(open => !open);
  }

  seedAndOpen(assistantContent: string): void {
    this._messages.update(msgs => [...msgs, { role: 'assistant', content: assistantContent }]);
    this._isOpen.set(true);
  }

  loadSessions(): void {
    this.http.get<ChatSessionSummaryResponse[]>('/api/chat/sessions')
      .pipe(catchError(() => of([])))
      .subscribe(items => this._sessions.set(items));
  }

  switchSession(sessionId: string): void {
    if (sessionId === this._sessionId) return;
    this.subscription?.unsubscribe();
    this._sessionId = sessionId;
    localStorage.setItem('hermes-chat-session', sessionId);
    this.isFreshConversation = false;
    this._messages.set([]);
    this._isStreaming.set(false);
    this.http.get<ChatMessageResponse[]>(`/api/chat/sessions/${sessionId}/messages`)
      .pipe(catchError(() => of([])))
      .subscribe(items => {
        if (this._sessionId !== sessionId) return;
        this._messages.set(items.map(m => ({
          role: m.role.toUpperCase() === 'USER' ? 'user' as const : 'assistant' as const,
          content: m.content,
        })));
      });
    if (this.client.connected) this.subscribe();
  }

  startNewChat(): void {
    this.subscription?.unsubscribe();
    this._sessionId = this.generateSessionId();
    this.isFreshConversation = true;
    this._messages.set([]);
    this._isStreaming.set(false);
    if (this.client.connected) this.subscribe();
  }

  deleteSession(sessionId: string): void {
    this.http.delete(`/api/chat/sessions/${sessionId}`)
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this._sessions.update(list => list.filter(s => s.sessionId !== sessionId));
        if (sessionId === this._sessionId) {
          this.startNewChat();
        }
      });
  }
}

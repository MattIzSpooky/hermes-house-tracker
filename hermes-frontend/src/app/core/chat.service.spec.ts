import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { ChatService } from './chat.service';
import { ChatSessionSummaryResponse, ChatMessageResponse } from './api.types';

describe('ChatService', () => {
  let service: ChatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('hermes-chat-session');

    const keycloakStub = {
      token: 'fake-token',
      updateToken: jasmine.createSpy('updateToken').and.resolveTo(false),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('hermes-chat-session');
  });

  it('loadSessions populates the sessions signal', () => {
    const response: ChatSessionSummaryResponse[] = [
      { sessionId: 'abc', title: 'Hello', lastMessageAt: '2026-06-01T08:00:00Z' },
    ];

    service.loadSessions();
    const req = httpMock.expectOne('/api/chat/sessions');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.sessions()).toEqual(response);
  });

  it('loadSessions sets an empty list on error', () => {
    service.loadSessions();
    const req = httpMock.expectOne('/api/chat/sessions');
    req.flush('error', { status: 500, statusText: 'Server Error' });

    expect(service.sessions()).toEqual([]);
  });

  it('switchSession updates sessionId, persists it, and loads messages', () => {
    const originalSessionId = service.sessionId;
    const targetSessionId = 'target-session-id';
    const history: ChatMessageResponse[] = [
      { role: 'USER', content: 'Hi', createdAt: '2026-06-01T08:00:00Z' },
      { role: 'ASSISTANT', content: 'Hello!', createdAt: '2026-06-01T08:00:01Z' },
    ];

    service.switchSession(targetSessionId);

    expect(service.sessionId).toBe(targetSessionId);
    expect(service.sessionId).not.toBe(originalSessionId);
    expect(localStorage.getItem('hermes-chat-session')).toBe(targetSessionId);

    const req = httpMock.expectOne(`/api/chat/sessions/${targetSessionId}/messages`);
    expect(req.request.method).toBe('GET');
    req.flush(history);

    expect(service.messages()).toEqual([
      { role: 'user', content: 'Hi' },
      { role: 'assistant', content: 'Hello!' },
    ]);
  });

  it('switchSession to the already-active session is a no-op', () => {
    const currentSessionId = service.sessionId;

    service.switchSession(currentSessionId);

    httpMock.expectNone(`/api/chat/sessions/${currentSessionId}/messages`);
  });

  it('startNewChat generates a fresh sessionId and clears messages', () => {
    const originalSessionId = service.sessionId;

    service.startNewChat();

    expect(service.sessionId).not.toBe(originalSessionId);
    expect(localStorage.getItem('hermes-chat-session')).toBe(service.sessionId);
    expect(service.messages()).toEqual([]);
  });

  it('deleteSession removes the session from the list', () => {
    service.loadSessions();
    httpMock.expectOne('/api/chat/sessions').flush([
      { sessionId: 'to-delete', title: 'Bye', lastMessageAt: '2026-06-01T08:00:00Z' },
    ]);

    service.deleteSession('to-delete');
    const req = httpMock.expectOne('/api/chat/sessions/to-delete');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(service.sessions()).toEqual([]);
  });

  it('deleteSession starts a new chat if the deleted session was active', () => {
    const activeSessionId = service.sessionId;

    service.deleteSession(activeSessionId);
    const req = httpMock.expectOne(`/api/chat/sessions/${activeSessionId}`);
    req.flush(null);

    expect(service.sessionId).not.toBe(activeSessionId);
    expect(service.messages()).toEqual([]);
  });
});

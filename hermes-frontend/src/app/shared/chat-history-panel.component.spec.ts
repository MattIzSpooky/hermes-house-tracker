import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ChatHistoryPanelComponent } from './chat-history-panel.component';
import { ChatService } from '../core/chat.service';
import { ChatSessionSummaryResponse } from '../core/api.types';

describe('ChatHistoryPanelComponent', () => {
  let fixture: ComponentFixture<ChatHistoryPanelComponent>;
  let el: HTMLElement;
  let chatSvc: jasmine.SpyObj<ChatService>;

  async function setup(sessions: ChatSessionSummaryResponse[] = [], activeSessionId = 'current-session') {
    chatSvc = jasmine.createSpyObj(
      'ChatService',
      ['loadSessions', 'switchSession', 'startNewChat', 'deleteSession'],
      {
        sessions: signal(sessions).asReadonly(),
        sessionId: activeSessionId,
      }
    );

    await TestBed.configureTestingModule({
      imports: [ChatHistoryPanelComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ChatService, useValue: chatSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatHistoryPanelComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('calls loadSessions on init', async () => {
    await setup();
    expect(chatSvc.loadSessions).toHaveBeenCalled();
  });

  it('renders a title for each session', async () => {
    await setup([
      { sessionId: 'a', title: 'First conversation', lastMessageAt: '2026-06-01T08:00:00Z' },
      { sessionId: 'b', title: 'Second conversation', lastMessageAt: '2026-06-02T08:00:00Z' },
    ]);
    expect(el.textContent).toContain('First conversation');
    expect(el.textContent).toContain('Second conversation');
  });

  it('clicking a conversation calls switchSession with its sessionId', async () => {
    await setup([{ sessionId: 'target', title: 'Click me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    el.querySelector<HTMLButtonElement>('[data-session-id="target"]')!.click();

    expect(chatSvc.switchSession).toHaveBeenCalledWith('target');
  });

  it('pressing Enter on a conversation row calls switchSession with its sessionId', async () => {
    await setup([{ sessionId: 'target', title: 'Press me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    const row = el.querySelector<HTMLElement>('[data-session-id="target"]')!;
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(chatSvc.switchSession).toHaveBeenCalledWith('target');
  });

  it('clicking delete calls deleteSession and does not also switch', async () => {
    await setup([{ sessionId: 'target', title: 'Delete me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    el.querySelector<HTMLButtonElement>('[data-delete-session-id="target"]')!.click();

    expect(chatSvc.deleteSession).toHaveBeenCalledWith('target');
    expect(chatSvc.switchSession).not.toHaveBeenCalled();
  });

  it('pressing Enter on delete calls deleteSession but does not also switch', async () => {
    await setup([{ sessionId: 'target', title: 'Delete me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    el.querySelector<HTMLButtonElement>('[data-delete-session-id="target"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(chatSvc.deleteSession).toHaveBeenCalledWith('target');
    expect(chatSvc.switchSession).not.toHaveBeenCalled();
  });

  it('pressing Space on a conversation row calls switchSession with its sessionId', async () => {
    await setup([{ sessionId: 'target', title: 'Press me', lastMessageAt: '2026-06-01T08:00:00Z' }]);

    const row = el.querySelector<HTMLElement>('[data-session-id="target"]')!;
    row.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

    expect(chatSvc.switchSession).toHaveBeenCalledWith('target');
  });

  it('clicking New chat calls startNewChat', async () => {
    await setup();

    el.querySelector<HTMLButtonElement>('[data-new-chat]')!.click();

    expect(chatSvc.startNewChat).toHaveBeenCalled();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ChatBubbleComponent } from './chat-bubble.component';
import { ChatService } from '../core/chat.service';

describe('ChatBubbleComponent', () => {
  let fixture: ComponentFixture<ChatBubbleComponent>;
  let el: HTMLElement;
  let chatSvc: jasmine.SpyObj<ChatService>;

  async function setup(isOpen = false, isStreaming = false): Promise<void> {
    chatSvc = jasmine.createSpyObj('ChatService', ['toggle', 'sendMessage'], {
      isOpen: () => isOpen,
      isStreaming: () => isStreaming,
      messages: signal([]).asReadonly(),
    });

    await TestBed.configureTestingModule({
      imports: [ChatBubbleComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ChatService, useValue: chatSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatBubbleComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('renders the toggle button', async () => {
    await setup();
    expect(el.querySelector('button[aria-label="Toggle chat"]')).toBeTruthy();
  });

  it('calls toggle() when button is clicked', async () => {
    await setup();
    el.querySelector<HTMLButtonElement>('button')!.click();
    expect(chatSvc.toggle).toHaveBeenCalled();
  });

  it('shows the chat panel when isOpen is true', async () => {
    await setup(true, false);
    expect(el.querySelector('app-chat-panel')).toBeTruthy();
  });

  it('hides the chat panel when isOpen is false', async () => {
    await setup(false, false);
    expect(el.querySelector('app-chat-panel')).toBeNull();
  });

  it('shows the pulsing ... indicator when isStreaming is true', async () => {
    await setup(false, true);
    const pulse = el.querySelector('.animate-pulse');
    expect(pulse).toBeTruthy();
    expect(pulse!.textContent?.trim()).toBe('...');
  });

  it('shows the Chat label when not streaming', async () => {
    await setup(false, false);
    expect(el.querySelector('.animate-pulse')).toBeNull();
    expect(el.querySelector('button')!.textContent?.trim()).toBe('Chat');
  });
});

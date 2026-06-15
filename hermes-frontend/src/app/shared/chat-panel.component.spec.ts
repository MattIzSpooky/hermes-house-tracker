import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ChatPanelComponent } from './chat-panel.component';
import { ChatService, ChatMessage } from '../core/chat.service';

describe('ChatPanelComponent', () => {
  let fixture: ComponentFixture<ChatPanelComponent>;
  let el: HTMLElement;
  let chatSvc: jasmine.SpyObj<ChatService>;

  function makeMessages(msgs: ChatMessage[]) {
    const s = signal(msgs);
    return s.asReadonly();
  }

  async function setup(messages: ChatMessage[] = [], isStreaming = false) {
    chatSvc = jasmine.createSpyObj('ChatService', ['sendMessage'], {
      messages: makeMessages(messages),
      isStreaming: () => isStreaming,
    });

    await TestBed.configureTestingModule({
      imports: [ChatPanelComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ChatService, useValue: chatSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatPanelComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('renders the input and send button', async () => {
    await setup();
    expect(el.querySelector('input')).toBeTruthy();
    expect(el.querySelector('button')).toBeTruthy();
  });

  it('calls sendMessage and clears input on send', async () => {
    await setup();
    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = 'I want a big house';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('button')!.click();
    expect(chatSvc.sendMessage).toHaveBeenCalledWith('I want a big house');
  });

  it('disables input and button while streaming', async () => {
    await setup([], true);
    expect(el.querySelector<HTMLInputElement>('input')!.disabled).toBeTrue();
    expect(el.querySelector<HTMLButtonElement>('button')!.disabled).toBeTrue();
  });
});

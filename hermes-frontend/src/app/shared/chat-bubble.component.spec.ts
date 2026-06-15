import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ChatBubbleComponent } from './chat-bubble.component';
import { ChatService } from '../core/chat.service';

describe('ChatBubbleComponent', () => {
  let fixture: ComponentFixture<ChatBubbleComponent>;
  let el: HTMLElement;
  let chatSvc: jasmine.SpyObj<ChatService>;

  beforeEach(async () => {
    chatSvc = jasmine.createSpyObj('ChatService', ['toggle'], {
      isOpen: () => false,
      isStreaming: () => false,
    });

    await TestBed.configureTestingModule({
      imports: [ChatBubbleComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ChatService, useValue: chatSvc },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatBubbleComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('renders the toggle button', () => {
    expect(el.querySelector('button[aria-label="Toggle chat"]')).toBeTruthy();
  });

  it('calls toggle() when button is clicked', () => {
    el.querySelector<HTMLButtonElement>('button')!.click();
    expect(chatSvc.toggle).toHaveBeenCalled();
  });
});

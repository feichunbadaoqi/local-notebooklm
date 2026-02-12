import { Component, input, output, signal, ElementRef, viewChild, effect } from '@angular/core';
import { ChatMessage, Citation } from '../../../../core/models';
import { ChatMessageComponent } from '../chat-message/chat-message.component';

@Component({
  selector: 'app-chat-area',
  standalone: true,
  imports: [ChatMessageComponent],
  host: {
    class: 'flex-1 flex flex-col min-h-0'
  },
  template: `
    <div class="flex flex-col flex-1 min-h-0">
      <!-- Messages area -->
      <div #messagesContainer class="flex-1 overflow-y-auto px-4 py-6 min-h-0">
        <div class="max-w-3xl mx-auto space-y-6">
          @if (loading()) {
            <div class="flex items-center justify-center py-8">
              <div class="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
            </div>
          } @else if (messages().length === 0 && !streaming()) {
            <!-- Empty state -->
            <div class="text-center py-16">
              <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-primary-light flex items-center justify-center">
                <svg class="w-8 h-8 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                </svg>
              </div>
              <h3 class="text-lg font-medium text-text-primary mb-2">Start a conversation</h3>
              <p class="text-text-muted max-w-sm mx-auto">
                Ask questions about your sources, explore topics, or get help understanding your documents.
              </p>
              <div class="mt-6 flex flex-wrap justify-center gap-2">
                @for (suggestion of suggestions; track suggestion) {
                  <button
                    class="px-4 py-2 text-sm bg-bg-sidebar hover:bg-bg-hover rounded-full text-text-primary transition-colors"
                    (click)="useSuggestion(suggestion)"
                  >
                    {{ suggestion }}
                  </button>
                }
              </div>
            </div>
          } @else {
            <!-- Message list -->
            @for (message of messages(); track message.id) {
              <app-chat-message
                [message]="message"
                (citationClick)="onCitationClick($event)"
              />
            }

            <!-- Streaming message -->
            @if (streaming()) {
              <div class="flex gap-4 animate-fadeIn">
                <div class="w-8 h-8 rounded-full bg-primary-light flex items-center justify-center flex-shrink-0">
                  <svg class="w-4 h-4 text-primary" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
                  </svg>
                </div>
                <div class="flex-1">
                  <div class="message-assistant p-4 inline-block max-w-full">
                    @if (streamContent()) {
                      <p class="whitespace-pre-wrap">{{ streamContent() }}</p>
                    } @else {
                      <div class="flex items-center gap-2">
                        <div class="w-2 h-2 bg-primary rounded-full animate-pulse"></div>
                        <div class="w-2 h-2 bg-primary rounded-full animate-pulse" style="animation-delay: 0.2s"></div>
                        <div class="w-2 h-2 bg-primary rounded-full animate-pulse" style="animation-delay: 0.4s"></div>
                      </div>
                    }
                  </div>
                  @if (citations().length > 0) {
                    <div class="mt-2 flex flex-wrap gap-1">
                      @for (citation of citations(); track citation.sourceNumber) {
                        <span class="citation" (click)="onCitationClick(citation)">
                          {{ citation.sourceNumber }}
                        </span>
                      }
                    </div>
                  }
                </div>
              </div>
            }
          }
        </div>
      </div>

      <!-- Input area -->
      <div class="border-t border-border p-4 bg-bg-main">
        <div class="max-w-3xl mx-auto">
          <div class="relative">
            <textarea
              #inputField
              class="input-field pr-12 resize-none min-h-[52px] max-h-40"
              placeholder="Ask a question about your sources..."
              [value]="inputValue()"
              (input)="onInput($event)"
              (keydown)="onKeyDown($event)"
              rows="1"
            ></textarea>
            <button
              class="absolute right-2 bottom-2 p-2 rounded-lg bg-primary text-white hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              [disabled]="!canSend()"
              (click)="send()"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"/>
              </svg>
            </button>
          </div>
          <p class="text-xs text-text-muted mt-2 text-center">
            Responses are based on your uploaded sources. Results may not be comprehensive.
          </p>
        </div>
      </div>
    </div>
  `
})
export class ChatAreaComponent {
  messages = input.required<ChatMessage[]>();
  streaming = input<boolean>(false);
  streamContent = input<string>('');
  citations = input<Citation[]>([]);
  loading = input<boolean>(false);

  sendMessage = output<string>();

  messagesContainer = viewChild<ElementRef>('messagesContainer');

  inputValue = signal('');

  suggestions = [
    'Summarize this document',
    'What are the key points?',
    'Explain this concept',
    'Compare these ideas'
  ];

  constructor() {
    // Auto-scroll when messages change
    effect(() => {
      const messages = this.messages();
      const streaming = this.streaming();
      const content = this.streamContent();
      this.scrollToBottom();
    });
  }

  canSend = () => this.inputValue().trim().length > 0 && !this.streaming();

  onInput(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    this.inputValue.set(textarea.value);
    // Auto-resize textarea
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 160) + 'px';
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && this.canSend()) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const message = this.inputValue().trim();
    if (message) {
      this.sendMessage.emit(message);
      this.inputValue.set('');
    }
  }

  useSuggestion(suggestion: string): void {
    this.inputValue.set(suggestion);
  }

  onCitationClick(citation: Citation): void {
    // Could open a panel showing the source content
    console.log('Citation clicked:', citation);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const container = this.messagesContainer()?.nativeElement;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }, 0);
  }
}

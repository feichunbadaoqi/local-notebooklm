import { Component, input, output } from '@angular/core';
import { ChatMessage, Citation } from '../../../../core/models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-chat-message',
  standalone: true,
  imports: [DatePipe],
  template: `
    @if (message().role === 'USER') {
      <!-- User message -->
      <div class="flex justify-end gap-4">
        <div class="max-w-[80%]">
          <div class="message-user p-4">
            <p class="whitespace-pre-wrap">{{ message().content }}</p>
          </div>
          <div class="text-xs text-text-muted mt-1 text-right">
            {{ message().createdAt | date:'shortTime' }}
          </div>
        </div>
        <div class="w-8 h-8 rounded-full bg-secondary flex items-center justify-center flex-shrink-0">
          <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
          </svg>
        </div>
      </div>
    } @else {
      <!-- Assistant message -->
      <div class="flex gap-4">
        <div class="w-8 h-8 rounded-full bg-primary-light flex items-center justify-center flex-shrink-0">
          <svg class="w-4 h-4 text-primary" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="flex-1 max-w-[80%]">
          <div class="message-assistant p-4">
            <div class="prose prose-sm max-w-none">
              @if (hasInlineCitations()) {
                <p [innerHTML]="getContentWithCitations()"></p>
              } @else {
                <p class="whitespace-pre-wrap">{{ message().content }}</p>
              }
            </div>
          </div>

          <!-- Citations -->
          @if (message().citations && message().citations!.length > 0) {
            <div class="mt-3 space-y-2">
              <div class="text-xs font-medium text-text-muted uppercase tracking-wide">Sources</div>
              <div class="flex flex-wrap gap-2">
                @for (citation of message().citations; track citation.sourceNumber) {
                  <button
                    class="flex items-center gap-2 px-3 py-1.5 bg-bg-sidebar hover:bg-bg-hover rounded-lg text-sm transition-colors group"
                    (click)="citationClick.emit(citation)"
                  >
                    <span class="citation">{{ citation.sourceNumber }}</span>
                    <span class="text-text-primary group-hover:text-primary truncate max-w-[200px]">
                      {{ citation.fileName }}
                    </span>
                  </button>
                }
              </div>
            </div>
          }

          <div class="flex items-center gap-4 mt-2 text-xs text-text-muted">
            <span>{{ message().createdAt | date:'shortTime' }}</span>
            @if (message().tokenCount > 0) {
              <span>&bull; {{ message().tokenCount }} tokens</span>
            }
            <div class="flex items-center gap-1 ml-auto">
              <button class="btn-icon p-1" aria-label="Copy">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/>
                </svg>
              </button>
              <button class="btn-icon p-1" aria-label="Like">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"/>
                </svg>
              </button>
              <button class="btn-icon p-1" aria-label="Dislike">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `
})
export class ChatMessageComponent {
  message = input.required<ChatMessage>();
  citationClick = output<Citation>();

  hasInlineCitations(): boolean {
    const citations = this.message().citations;
    return this.message().content.includes('[') && !!citations && citations.length > 0;
  }

  getContentWithCitations(): string {
    let content = this.message().content;
    const citations = this.message().citations || [];

    // Replace [1], [2], etc. with styled citation spans
    for (const citation of citations) {
      const pattern = new RegExp(`\\[${citation.sourceNumber}\\]`, 'g');
      content = content.replace(
        pattern,
        `<span class="citation cursor-pointer" data-source="${citation.sourceNumber}">${citation.sourceNumber}</span>`
      );
    }

    return content;
  }
}

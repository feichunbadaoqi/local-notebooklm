import { Injectable, inject, signal, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of, Subject } from 'rxjs';
import { ChatMessage, StreamChunk, Citation } from '../models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private http = inject(HttpClient);
  private ngZone = inject(NgZone);
  private apiUrl = `${environment.apiUrl}/sessions`;

  // Reactive state
  private messagesSignal = signal<ChatMessage[]>([]);
  private streamingSignal = signal(false);
  private currentStreamContentSignal = signal('');
  private currentCitationsSignal = signal<Citation[]>([]);
  private loadingSignal = signal(false);
  private errorSignal = signal<string | null>(null);

  readonly messages = this.messagesSignal.asReadonly();
  readonly streaming = this.streamingSignal.asReadonly();
  readonly currentStreamContent = this.currentStreamContentSignal.asReadonly();
  readonly currentCitations = this.currentCitationsSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  // Subject for streaming events
  private streamEvents = new Subject<StreamChunk>();
  readonly streamEvents$ = this.streamEvents.asObservable();

  private eventSource: EventSource | null = null;

  loadMessages(sessionId: string, limit = 50): void {
    this.loadingSignal.set(true);

    this.http.get<ChatMessage[]>(`${this.apiUrl}/${sessionId}/messages?limit=${limit}`).pipe(
      tap(messages => {
        this.messagesSignal.set(messages);
        this.loadingSignal.set(false);
      }),
      catchError(error => {
        console.error('Error loading messages:', error);
        this.loadingSignal.set(false);
        return of([]);
      })
    ).subscribe();
  }

  sendMessage(sessionId: string, message: string): void {
    // Add user message immediately to UI
    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      sessionId,
      role: 'USER',
      content: message,
      modeUsed: 'EXPLORING',
      tokenCount: 0,
      createdAt: new Date().toISOString()
    };
    this.messagesSignal.update(messages => [...messages, userMessage]);

    // Reset streaming state
    this.streamingSignal.set(true);
    this.currentStreamContentSignal.set('');
    this.currentCitationsSignal.set([]);
    this.errorSignal.set(null);

    // Close any existing connection
    this.closeStream();

    // Create SSE connection
    const url = `${this.apiUrl}/${sessionId}/chat/stream`;

    // Use fetch with POST for SSE (EventSource only supports GET)
    this.streamWithFetch(url, message, sessionId);
  }

  private streamWithFetch(url: string, message: string, sessionId: string): void {
    const controller = new AbortController();

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({ message }),
      signal: controller.signal
    }).then(async response => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('No response body');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6);
            if (data === '[DONE]') continue;

            try {
              const chunk: StreamChunk = JSON.parse(data);
              this.ngZone.run(() => this.handleStreamChunk(chunk, sessionId));
            } catch {
              // Ignore parse errors for malformed chunks
            }
          }
        }
      }
    }).catch(error => {
      this.ngZone.run(() => {
        this.streamingSignal.set(false);
        this.errorSignal.set(error.message || 'Stream error');
        this.streamEvents.next({
          eventType: 'error',
          errorMessage: error.message
        });
      });
    });
  }

  private handleStreamChunk(chunk: StreamChunk, sessionId: string): void {
    this.streamEvents.next(chunk);

    switch (chunk.eventType) {
      case 'token':
        if (chunk.content) {
          this.currentStreamContentSignal.update(content => content + chunk.content);
        }
        break;

      case 'citation':
        if (chunk.citation) {
          this.currentCitationsSignal.update(citations => [...citations, chunk.citation!]);
        }
        break;

      case 'done':
        // Add complete assistant message
        const assistantMessage: ChatMessage = {
          id: chunk.messageId || crypto.randomUUID(),
          sessionId,
          role: 'ASSISTANT',
          content: this.currentStreamContentSignal(),
          modeUsed: 'EXPLORING',
          citations: this.currentCitationsSignal(),
          tokenCount: chunk.totalTokens || 0,
          createdAt: new Date().toISOString()
        };
        this.messagesSignal.update(messages => [...messages, assistantMessage]);
        this.streamingSignal.set(false);
        this.currentStreamContentSignal.set('');
        break;

      case 'error':
        this.errorSignal.set(chunk.errorMessage || 'Unknown error');
        this.streamingSignal.set(false);
        break;
    }
  }

  closeStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  clearMessages(): void {
    this.messagesSignal.set([]);
    this.currentStreamContentSignal.set('');
    this.currentCitationsSignal.set([]);
  }

  compactHistory(sessionId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${sessionId}/compact`, {}).pipe(
      tap(() => {
        // Reload messages after compaction
        this.loadMessages(sessionId);
      })
    );
  }
}

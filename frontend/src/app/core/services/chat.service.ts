import { Injectable, inject, signal, ApplicationRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of, Subject } from 'rxjs';
import { ChatMessage, StreamChunk, Citation } from '../models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private http = inject(HttpClient);
  private appRef = inject(ApplicationRef);
  private apiUrl = `${environment.apiUrl}/sessions`;

  // Reactive state
  private messagesSignal = signal<ChatMessage[]>([]);
  private streamingSignal = signal(false);
  private currentStreamContentSignal = signal('');
  private currentCitationsSignal = signal<Citation[]>([]);
  private loadingSignal = signal(false);
  private errorSignal = signal<string | null>(null);

  // Track citation numbers per message
  private citationNumberMap = new Map<string, number>();
  private currentCitationCounter = 0;

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

    // Reset citation tracking for new message
    this.citationNumberMap.clear();
    this.currentCitationCounter = 0;

    // Close any existing connection
    this.closeStream();

    // Create SSE connection
    const url = `${this.apiUrl}/${sessionId}/chat/stream`;

    // Use fetch with POST for SSE (EventSource only supports GET)
    this.streamWithFetch(url, message, sessionId);
  }

  private streamWithFetch(url: string, message: string, sessionId: string): void {
    const controller = new AbortController();

    console.log('[ChatService] Starting SSE fetch to:', url);

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({ message }),
      signal: controller.signal
    }).then(async response => {
      console.log('[ChatService] SSE response status:', response.status);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('No response body');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      console.log('[ChatService] Starting to read SSE stream...');

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          console.log('[ChatService] SSE stream ended');
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data === '[DONE]') {
              console.log('[ChatService] Received [DONE] marker');
              continue;
            }

            try {
              console.log('[ChatService] Raw SSE data:', data);
              const chunk = JSON.parse(data);
              console.log('[ChatService] Parsed chunk:', chunk);
              this.handleStreamChunk(chunk, sessionId);
              // Force change detection in zoneless Angular
              this.appRef.tick();
            } catch (e) {
              console.warn('[ChatService] Failed to parse SSE chunk:', data, e);
            }
          }
        }
      }

      // Ensure streaming is stopped even if no 'done' event
      if (this.streamingSignal()) {
        console.log('[ChatService] Stream ended without done event, stopping...');
        this.streamingSignal.set(false);
        this.appRef.tick();
      }

    }).catch(error => {
      console.error('[ChatService] SSE error:', error);
      this.streamingSignal.set(false);
      this.errorSignal.set(error.message || 'Stream error');
      this.streamEvents.next({
        eventType: 'error',
        errorMessage: error.message
      });
      this.appRef.tick();
    });
  }

  private handleStreamChunk(rawChunk: any, sessionId: string): void {
    // Parse backend format: {eventType, data: {...}} into frontend format
    const chunk = this.parseBackendChunk(rawChunk);
    console.log('[ChatService] handleStreamChunk:', chunk.eventType, chunk);

    this.streamEvents.next(chunk);

    switch (chunk.eventType) {
      case 'token':
        if (chunk.content) {
          this.currentStreamContentSignal.update(content => content + chunk.content);
          console.log('[ChatService] Stream content updated, length:', this.currentStreamContentSignal().length);
        }
        break;

      case 'citation':
        if (chunk.citation) {
          this.currentCitationsSignal.update(citations => [...citations, chunk.citation!]);
          console.log('[ChatService] Citation added:', chunk.citation);
        }
        break;

      case 'done':
        console.log('[ChatService] Done event received, finalizing message...');
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
        console.log('[ChatService] Adding assistant message:', assistantMessage.content.substring(0, 100));
        this.messagesSignal.update(messages => [...messages, assistantMessage]);
        this.streamingSignal.set(false);
        this.currentStreamContentSignal.set('');
        console.log('[ChatService] Messages count after done:', this.messagesSignal().length);
        break;

      case 'error':
        console.error('[ChatService] Error event:', chunk.errorMessage);
        this.errorSignal.set(chunk.errorMessage || 'Unknown error');
        this.streamingSignal.set(false);
        break;
    }
  }

  /**
   * Parse backend SSE format into frontend StreamChunk format.
   * Backend sends: {eventType, data: {...}}
   * Frontend expects: {eventType, content?, citation?, messageId?, ...}
   */
  private parseBackendChunk(raw: any): StreamChunk {
    const eventType = raw.eventType;
    const data = raw.data || {};

    switch (eventType) {
      case 'token':
        return { eventType, content: data.content };
      case 'citation':
        const documentId = data.documentId || '';
        const fileName = data.source || '';
        // Assign unique citation numbers, deduplicating by documentId
        let sourceNumber = this.citationNumberMap.get(documentId);
        if (sourceNumber === undefined) {
          sourceNumber = ++this.currentCitationCounter;
          this.citationNumberMap.set(documentId, sourceNumber);
        }
        return {
          eventType,
          citation: {
            sourceNumber,
            documentId,
            fileName,
            content: data.text || '',
            chunkId: '',
            imageIds: data.imageIds || [],
            sectionBreadcrumb: data.sectionBreadcrumb || []
          }
        };
      case 'done':
        return {
          eventType,
          messageId: data.messageId,
          totalTokens: data.completionTokens || 0
        };
      case 'error':
        return {
          eventType,
          errorMessage: data.message || 'Unknown error'
        };
      default:
        return { eventType: 'error', errorMessage: `Unknown event type: ${eventType}` };
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

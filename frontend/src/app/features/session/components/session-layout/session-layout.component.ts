import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SessionService, DocumentService, ChatService } from '../../../../core/services';
import { SourcesPanelComponent } from '../../../document/components/sources-panel/sources-panel.component';
import { ChatAreaComponent } from '../../../chat/components/chat-area/chat-area.component';
import { HeaderComponent } from '../../../../shared/components/header/header.component';

@Component({
  selector: 'app-session-layout',
  standalone: true,
  imports: [SourcesPanelComponent, ChatAreaComponent, HeaderComponent],
  host: {
    class: 'block h-full'
  },
  template: `
    <div class="flex flex-col h-screen bg-bg-main">
      <!-- Header -->
      <app-header
        [sessionTitle]="sessionService.currentSession()?.title ?? 'Untitled Notebook'"
        [currentMode]="sessionService.currentMode()"
        (modeChange)="onModeChange($event)"
      />

      <!-- Main content -->
      <div class="flex flex-1 overflow-hidden">
        <!-- Left sidebar - Sources panel -->
        <aside class="w-80 border-r border-border bg-bg-sidebar flex flex-col overflow-hidden">
          <app-sources-panel
            [documents]="documentService.documents()"
            [loading]="documentService.loading()"
            (uploadClick)="showUploadModal.set(true)"
            (documentSelect)="onDocumentSelect($event)"
            (documentDelete)="onDocumentDelete($event)"
          />
        </aside>

        <!-- Main chat area -->
        <main class="flex-1 flex flex-col overflow-hidden">
          <app-chat-area
            [messages]="chatService.messages()"
            [streaming]="chatService.streaming()"
            [streamContent]="chatService.currentStreamContent()"
            [citations]="chatService.currentCitations()"
            [loading]="chatService.loading()"
            (sendMessage)="onSendMessage($event)"
          />
        </main>
      </div>

      <!-- Upload modal -->
      @if (showUploadModal()) {
        <div class="modal-overlay animate-fadeIn" (click)="showUploadModal.set(false)">
          <div class="modal-content p-6 animate-slideIn" (click)="$event.stopPropagation()">
            <div class="flex items-center justify-between mb-6">
              <h2 class="text-xl font-semibold text-text-primary">Add sources</h2>
              <button
                class="btn-icon"
                (click)="showUploadModal.set(false)"
                aria-label="Close"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <div class="space-y-4">
              <!-- File upload area -->
              <div
                class="border-2 border-dashed border-border rounded-xl p-8 text-center hover:border-primary hover:bg-primary-light/30 transition-colors cursor-pointer"
                (click)="fileInput.click()"
                (dragover)="onDragOver($event)"
                (drop)="onFileDrop($event)"
              >
                <input
                  #fileInput
                  type="file"
                  class="hidden"
                  multiple
                  accept=".pdf,.doc,.docx,.txt,.epub,.md"
                  (change)="onFileSelect($event)"
                />
                <svg class="w-12 h-12 mx-auto mb-4 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
                </svg>
                <p class="text-text-primary font-medium mb-1">Upload files</p>
                <p class="text-sm text-text-muted">PDF, Word, TXT, EPUB, Markdown</p>
              </div>

              <!-- Supported formats info -->
              <div class="flex items-center gap-2 text-sm text-text-muted">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
                <span>Up to 50 sources per notebook. Maximum 500,000 words per source.</span>
              </div>
            </div>

            <!-- Upload progress -->
            @if (uploadProgress().size > 0) {
              <div class="mt-6 space-y-3">
                @for (progress of uploadProgressArray(); track progress.fileName) {
                  <div class="flex items-center gap-3 p-3 bg-bg-sidebar rounded-lg">
                    <div class="flex-1">
                      <div class="flex items-center justify-between mb-1">
                        <span class="text-sm font-medium text-text-primary truncate">{{ progress.fileName }}</span>
                        <span class="text-xs text-text-muted">{{ progress.progress }}%</span>
                      </div>
                      <div class="h-1.5 bg-border rounded-full overflow-hidden">
                        <div
                          class="h-full bg-primary transition-all duration-300"
                          [style.width.%]="progress.progress"
                        ></div>
                      </div>
                    </div>
                    @if (progress.status === 'complete') {
                      <svg class="w-5 h-5 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
                      </svg>
                    }
                    @if (progress.status === 'error') {
                      <svg class="w-5 h-5 text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                      </svg>
                    }
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class SessionLayoutComponent implements OnInit {
  private route = inject(ActivatedRoute);
  sessionService = inject(SessionService);
  documentService = inject(DocumentService);
  chatService = inject(ChatService);

  showUploadModal = signal(false);
  uploadProgress = this.documentService.uploadProgress;

  uploadProgressArray = () => Array.from(this.uploadProgress().values());

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('id');
    if (sessionId) {
      this.sessionService.getSession(sessionId).subscribe();
      this.documentService.loadDocuments(sessionId);
      this.chatService.loadMessages(sessionId);
    }
  }

  onModeChange(mode: string): void {
    this.sessionService.updateMode(mode as any).subscribe();
  }

  onDocumentSelect(documentId: string): void {
    // Could show document preview or highlight related citations
    console.log('Document selected:', documentId);
  }

  onDocumentDelete(documentId: string): void {
    const session = this.sessionService.currentSession();
    if (session) {
      this.documentService.deleteDocument(session.id, documentId).subscribe();
    }
  }

  onSendMessage(message: string): void {
    const session = this.sessionService.currentSession();
    if (session) {
      this.chatService.sendMessage(session.id, message);
    }
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.uploadFiles(Array.from(input.files));
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer?.files) {
      this.uploadFiles(Array.from(event.dataTransfer.files));
    }
  }

  private uploadFiles(files: File[]): void {
    const session = this.sessionService.currentSession();
    if (!session) return;

    for (const file of files) {
      this.documentService.uploadDocument(session.id, file).subscribe({
        error: (err) => console.error('Upload failed:', err)
      });
    }
  }
}

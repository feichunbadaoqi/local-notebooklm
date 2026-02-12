import { Component, input, output, signal } from '@angular/core';
import { Document, DocumentStatus } from '../../../../core/models';

@Component({
  selector: 'app-sources-panel',
  standalone: true,
  host: {
    class: 'flex-1 flex flex-col min-h-0'
  },
  template: `
    <div class="flex flex-col flex-1 min-h-0">
      <!-- Header -->
      <div class="p-4 border-b border-border">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-sm font-semibold text-text-primary uppercase tracking-wide">Sources</h2>
          <button
            class="btn btn-primary text-sm py-1.5 px-3"
            (click)="uploadClick.emit()"
          >
            <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
            </svg>
            Add
          </button>
        </div>

        <!-- Search -->
        <div class="relative">
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          <input
            type="text"
            class="input-field pl-10 py-2 text-sm"
            placeholder="Search sources..."
            [value]="searchQuery()"
            (input)="onSearch($event)"
          />
        </div>
      </div>

      <!-- Document list -->
      <div class="flex-1 overflow-y-auto p-2 min-h-0">
        @if (loading()) {
          <div class="flex items-center justify-center py-8">
            <div class="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
          </div>
        } @else if (filteredDocuments().length === 0) {
          <div class="text-center py-8">
            <svg class="w-12 h-12 mx-auto mb-3 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <p class="text-text-muted text-sm">
              @if (searchQuery()) {
                No sources match your search
              } @else {
                No sources added yet
              }
            </p>
            @if (!searchQuery()) {
              <button
                class="mt-3 text-primary text-sm font-medium hover:underline"
                (click)="uploadClick.emit()"
              >
                Add your first source
              </button>
            }
          </div>
        } @else {
          <div class="space-y-1">
            @for (doc of filteredDocuments(); track doc.id; let i = $index) {
              <div
                class="source-item group"
                [class.source-item-active]="selectedDocumentId() === doc.id"
                (click)="selectDocument(doc.id)"
              >
                <!-- Document icon with processing spinner -->
                <div class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                     [class]="doc.status === 'PROCESSING' || doc.status === 'PENDING' ? 'bg-amber-100' : 'bg-primary-light'">
                  @if (doc.status === 'PROCESSING' || doc.status === 'PENDING') {
                    <div class="w-5 h-5 border-2 border-amber-500 border-t-transparent rounded-full animate-spin"></div>
                  } @else if (doc.status === 'FAILED') {
                    <svg class="w-5 h-5 text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                    </svg>
                  } @else {
                    <span class="text-primary font-semibold text-sm">{{ i + 1 }}</span>
                  }
                </div>

                <!-- Document info -->
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-text-primary truncate">{{ doc.fileName }}</span>
                    @if (doc.status !== 'READY') {
                      <span class="badge" [class]="getStatusBadgeClass(doc.status)">
                        {{ getStatusLabel(doc.status) }}
                      </span>
                    }
                  </div>
                  <div class="flex items-center gap-2 text-xs text-text-muted mt-0.5">
                    <span>{{ getFileTypeLabel(doc.mimeType) }}</span>
                    @if (doc.chunkCount > 0) {
                      <span>&bull;</span>
                      <span>{{ doc.chunkCount }} chunks</span>
                    }
                  </div>
                </div>

                <!-- Actions -->
                <div class="opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    class="btn-icon p-1.5"
                    (click)="deleteDocument(doc.id, $event)"
                    aria-label="Delete document"
                  >
                    <svg class="w-4 h-4 text-text-muted hover:text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                    </svg>
                  </button>
                </div>
              </div>
            }
          </div>
        }
      </div>

      <!-- Footer with count -->
      @if (documents().length > 0) {
        <div class="p-3 border-t border-border text-xs text-text-muted">
          {{ documents().length }} source{{ documents().length === 1 ? '' : 's' }}
        </div>
      }
    </div>
  `
})
export class SourcesPanelComponent {
  documents = input.required<Document[]>();
  loading = input<boolean>(false);
  uploadClick = output<void>();
  documentSelect = output<string>();
  documentDelete = output<string>();

  searchQuery = signal('');
  selectedDocumentId = signal<string | null>(null);

  filteredDocuments = () => {
    const query = this.searchQuery().toLowerCase();
    if (!query) return this.documents();
    return this.documents().filter(doc =>
      doc.fileName.toLowerCase().includes(query)
    );
  };

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  selectDocument(id: string): void {
    this.selectedDocumentId.set(id);
    this.documentSelect.emit(id);
  }

  deleteDocument(id: string, event: Event): void {
    event.stopPropagation();
    this.documentDelete.emit(id);
  }

  getStatusLabel(status: DocumentStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Pending';
      case 'PROCESSING':
        return 'Processing';
      case 'READY':
        return 'Ready';
      case 'FAILED':
        return 'Failed';
      default:
        return status;
    }
  }

  getStatusBadgeClass(status: DocumentStatus): string {
    switch (status) {
      case 'PROCESSING':
        return 'badge-primary';
      case 'READY':
        return 'badge-success';
      case 'FAILED':
        return 'bg-red-100 text-danger';
      default:
        return 'badge-warning';
    }
  }

  getFileTypeLabel(mimeType: string): string {
    if (mimeType.includes('pdf')) return 'PDF';
    if (mimeType.includes('word') || mimeType.includes('docx')) return 'Word';
    if (mimeType.includes('epub')) return 'EPUB';
    if (mimeType.includes('text') || mimeType.includes('plain')) return 'Text';
    if (mimeType.includes('markdown')) return 'Markdown';
    return 'Document';
  }
}

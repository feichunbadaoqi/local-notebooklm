import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable, tap, map, catchError, of, filter, interval, takeWhile, switchMap, Subscription } from 'rxjs';
import { Document, DocumentUploadResponse } from '../models';
import { environment } from '../../../environments/environment';

export interface UploadProgress {
  fileName: string;
  progress: number;
  status: 'uploading' | 'processing' | 'complete' | 'error';
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DocumentService implements OnDestroy {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/sessions`;
  private documentsApiUrl = `${environment.apiUrl}/documents`;
  private statusPollingSubscriptions = new Map<string, Subscription>();

  // Reactive state
  private documentsSignal = signal<Document[]>([]);
  private uploadProgressSignal = signal<Map<string, UploadProgress>>(new Map());
  private loadingSignal = signal(false);

  readonly documents = this.documentsSignal.asReadonly();
  readonly uploadProgress = this.uploadProgressSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();

  ngOnDestroy(): void {
    // Clean up all polling subscriptions
    this.statusPollingSubscriptions.forEach(sub => sub.unsubscribe());
    this.statusPollingSubscriptions.clear();
  }

  loadDocuments(sessionId: string): void {
    this.loadingSignal.set(true);

    this.http.get<Document[]>(`${this.apiUrl}/${sessionId}/documents`).pipe(
      tap(documents => {
        this.documentsSignal.set(documents);
        this.loadingSignal.set(false);
      }),
      catchError(error => {
        console.error('Error loading documents:', error);
        this.loadingSignal.set(false);
        return of([]);
      })
    ).subscribe();
  }

  uploadDocument(sessionId: string, file: File): Observable<DocumentUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);

    // Initialize upload progress
    this.updateUploadProgress(file.name, {
      fileName: file.name,
      progress: 0,
      status: 'uploading'
    });

    return this.http.post<DocumentUploadResponse>(
      `${this.apiUrl}/${sessionId}/documents`,
      formData,
      {
        reportProgress: true,
        observe: 'events'
      }
    ).pipe(
      tap((event: HttpEvent<DocumentUploadResponse>) => {
        if (event.type === HttpEventType.UploadProgress) {
          const progress = event.total ? Math.round(100 * event.loaded / event.total) : 0;
          this.updateUploadProgress(file.name, {
            fileName: file.name,
            progress,
            status: progress < 100 ? 'uploading' : 'processing'
          });
        }
      }),
      filter((event): event is HttpResponse<DocumentUploadResponse> =>
        event.type === HttpEventType.Response
      ),
      map(event => event.body!),
      tap(response => {
        // Upload complete, now start polling for processing status
        this.updateUploadProgress(file.name, {
          fileName: file.name,
          progress: 100,
          status: 'processing'
        });

        // Immediately add document to the list with PENDING/PROCESSING status
        if (response.id) {
          const newDoc: Document = {
            id: response.id,
            sessionId: sessionId,
            fileName: response.fileName || file.name,
            mimeType: file.type || 'application/octet-stream',
            status: response.status || 'PROCESSING',
            chunkCount: 0,
            fileSize: file.size,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
          };

          // Add to documents list immediately
          this.documentsSignal.update(docs => {
            // Check if document already exists (avoid duplicates)
            if (docs.some(d => d.id === newDoc.id)) {
              return docs;
            }
            return [newDoc, ...docs];
          });

          // Start polling for document processing status
          this.startStatusPolling(sessionId, response.id, file.name);
        }
      }),
      catchError(error => {
        this.updateUploadProgress(file.name, {
          fileName: file.name,
          progress: 0,
          status: 'error',
          error: error.message || 'Upload failed'
        });
        throw error;
      })
    );
  }

  deleteDocument(sessionId: string, documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}/documents/${documentId}`).pipe(
      tap(() => {
        this.documentsSignal.update(docs => docs.filter(d => d.id !== documentId));
      })
    );
  }

  getDocument(sessionId: string, documentId: string): Observable<Document> {
    return this.http.get<Document>(`${this.apiUrl}/${sessionId}/documents/${documentId}`);
  }

  clearDocuments(): void {
    this.documentsSignal.set([]);
  }

  clearUploadProgress(fileName: string): void {
    this.uploadProgressSignal.update(map => {
      const newMap = new Map(map);
      newMap.delete(fileName);
      return newMap;
    });
  }

  private updateUploadProgress(fileName: string, progress: UploadProgress): void {
    this.uploadProgressSignal.update(map => {
      const newMap = new Map(map);
      newMap.set(fileName, progress);
      return newMap;
    });
  }

  /**
   * Poll for document processing status until complete or failed
   */
  private startStatusPolling(sessionId: string, documentId: string, fileName: string): void {
    console.debug(`Starting status polling for document ${documentId}`);

    // Stop any existing polling for this document
    this.stopStatusPolling(documentId);

    const subscription = interval(2000).pipe( // Poll every 2 seconds
      switchMap(() => this.getDocumentStatus(documentId)),
      takeWhile(doc => doc.status === 'PENDING' || doc.status === 'PROCESSING', true)
    ).subscribe({
      next: (doc) => {
        console.debug(`Document ${documentId} status: ${doc.status}`);

        // Update the document in the list with latest status
        this.updateDocumentInList(doc);

        if (doc.status === 'READY') {
          this.updateUploadProgress(fileName, {
            fileName,
            progress: 100,
            status: 'complete'
          });
          this.stopStatusPolling(documentId);
          // Auto-clear progress after 3 seconds
          setTimeout(() => this.clearUploadProgress(fileName), 3000);
        } else if (doc.status === 'FAILED') {
          this.updateUploadProgress(fileName, {
            fileName,
            progress: 0,
            status: 'error',
            error: doc.processingError || 'Processing failed'
          });
          this.stopStatusPolling(documentId);
        }
      },
      error: (err) => {
        console.error('Error polling document status:', err);
        this.updateUploadProgress(fileName, {
          fileName,
          progress: 0,
          status: 'error',
          error: 'Failed to check processing status'
        });
        this.stopStatusPolling(documentId);
      }
    });

    this.statusPollingSubscriptions.set(documentId, subscription);
  }

  private stopStatusPolling(documentId: string): void {
    const subscription = this.statusPollingSubscriptions.get(documentId);
    if (subscription) {
      subscription.unsubscribe();
      this.statusPollingSubscriptions.delete(documentId);
    }
  }

  private getDocumentStatus(documentId: string): Observable<Document> {
    return this.http.get<Document>(`${this.documentsApiUrl}/${documentId}/status`);
  }

  /**
   * Update a document in the documents list with new data
   */
  private updateDocumentInList(updatedDoc: Document): void {
    this.documentsSignal.update(docs => {
      const index = docs.findIndex(d => d.id === updatedDoc.id);
      if (index === -1) {
        // Document not in list, add it
        return [updatedDoc, ...docs];
      }
      // Replace the document with updated data
      const newDocs = [...docs];
      newDocs[index] = updatedDoc;
      return newDocs;
    });
  }
}

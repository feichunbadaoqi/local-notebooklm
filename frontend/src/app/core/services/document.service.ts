import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable, tap, map, catchError, of, filter } from 'rxjs';
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
export class DocumentService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/sessions`;

  // Reactive state
  private documentsSignal = signal<Document[]>([]);
  private uploadProgressSignal = signal<Map<string, UploadProgress>>(new Map());
  private loadingSignal = signal(false);

  readonly documents = this.documentsSignal.asReadonly();
  readonly uploadProgress = this.uploadProgressSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();

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
        this.updateUploadProgress(file.name, {
          fileName: file.name,
          progress: 100,
          status: 'complete'
        });
        // Refresh document list
        this.loadDocuments(sessionId);
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
}

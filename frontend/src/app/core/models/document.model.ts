export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface Document {
  id: string;
  sessionId: string;
  fileName: string;
  mimeType: string;
  status: DocumentStatus;
  chunkCount: number;
  fileSize: number;
  summary?: string;
  errorMessage?: string;
  processingError?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentUploadResponse {
  id: string;
  fileName: string;
  status: DocumentStatus;
}

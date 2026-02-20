import { InteractionMode } from './session.model';

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: MessageRole;
  content: string;
  modeUsed: InteractionMode;
  citations?: Citation[];
  tokenCount: number;
  createdAt: string;
}

export interface Citation {
  sourceNumber: number;
  documentId: string;
  fileName: string;
  content: string;
  chunkId: string;
  /** UUIDs of images extracted from the cited document chunk. */
  imageIds?: string[];
  /** Hierarchical breadcrumb path showing section context, e.g. ["Chapter 1", "Security", "Best Practices"]. */
  sectionBreadcrumb?: string[];
}

export interface SendMessageRequest {
  message: string;
}

export interface StreamChunk {
  eventType: 'token' | 'citation' | 'done' | 'error';
  content?: string;
  citation?: Citation;
  messageId?: string;
  totalTokens?: number;
  errorMessage?: string;
}

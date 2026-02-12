export type InteractionMode = 'EXPLORING' | 'RESEARCH' | 'LEARNING';

export interface Session {
  id: string;
  title: string;
  currentMode: InteractionMode;
  documentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSessionRequest {
  title: string;
}

export interface UpdateModeRequest {
  mode: InteractionMode;
}

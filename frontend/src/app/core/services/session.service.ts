import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of } from 'rxjs';
import { Session, CreateSessionRequest, UpdateModeRequest, InteractionMode } from '../models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/sessions`;

  // Reactive state using signals
  private sessionsSignal = signal<Session[]>([]);
  private currentSessionSignal = signal<Session | null>(null);
  private loadingSignal = signal(false);
  private errorSignal = signal<string | null>(null);

  // Public readonly signals
  readonly sessions = this.sessionsSignal.asReadonly();
  readonly currentSession = this.currentSessionSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  // Computed signals
  readonly currentMode = computed(() => this.currentSessionSignal()?.currentMode ?? 'EXPLORING');
  readonly hasSession = computed(() => this.currentSessionSignal() !== null);

  loadSessions(): void {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);

    this.http.get<Session[]>(this.apiUrl).pipe(
      tap(sessions => {
        this.sessionsSignal.set(sessions);
        this.loadingSignal.set(false);
      }),
      catchError(error => {
        this.errorSignal.set('Failed to load sessions');
        this.loadingSignal.set(false);
        console.error('Error loading sessions:', error);
        return of([]);
      })
    ).subscribe();
  }

  createSession(title: string): Observable<Session> {
    const request: CreateSessionRequest = { title };
    return this.http.post<Session>(this.apiUrl, request).pipe(
      tap(session => {
        this.sessionsSignal.update(sessions => [session, ...sessions]);
        this.setCurrentSession(session);
      })
    );
  }

  getSession(id: string): Observable<Session> {
    return this.http.get<Session>(`${this.apiUrl}/${id}`).pipe(
      tap(session => this.setCurrentSession(session))
    );
  }

  setCurrentSession(session: Session | null): void {
    this.currentSessionSignal.set(session);
  }

  updateMode(mode: InteractionMode): Observable<Session> {
    const session = this.currentSessionSignal();
    if (!session) {
      throw new Error('No session selected');
    }

    const request: UpdateModeRequest = { mode };
    return this.http.put<Session>(`${this.apiUrl}/${session.id}/mode`, request).pipe(
      tap(updatedSession => {
        this.currentSessionSignal.set(updatedSession);
        this.sessionsSignal.update(sessions =>
          sessions.map(s => s.id === updatedSession.id ? updatedSession : s)
        );
      })
    );
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        this.sessionsSignal.update(sessions => sessions.filter(s => s.id !== id));
        if (this.currentSessionSignal()?.id === id) {
          this.currentSessionSignal.set(null);
        }
      })
    );
  }
}

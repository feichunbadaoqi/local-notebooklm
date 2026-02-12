import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../../../core/services';
import { Session } from '../../../../core/models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-session-list',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="min-h-screen bg-bg-sidebar">
      <!-- Header -->
      <header class="bg-bg-main border-b border-border">
        <div class="max-w-6xl mx-auto px-6 py-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <svg class="w-10 h-10 text-primary" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
              </svg>
              <div>
                <h1 class="text-xl font-semibold text-text-primary">NotebookLM</h1>
                <p class="text-sm text-text-muted">AI-powered document assistant</p>
              </div>
            </div>
          </div>
        </div>
      </header>

      <!-- Main content -->
      <main class="max-w-6xl mx-auto px-6 py-8">
        <!-- Create new notebook section -->
        <section class="mb-12">
          <h2 class="text-lg font-medium text-text-primary mb-4">Create a new notebook</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <!-- Create new card -->
            <button
              class="card p-6 text-left hover:shadow-md transition-shadow group"
              (click)="showCreateModal.set(true)"
            >
              <div class="w-12 h-12 rounded-xl bg-primary-light flex items-center justify-center mb-4 group-hover:bg-primary transition-colors">
                <svg class="w-6 h-6 text-primary group-hover:text-white transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
                </svg>
              </div>
              <h3 class="font-medium text-text-primary mb-1">New notebook</h3>
              <p class="text-sm text-text-muted">Start fresh with a blank notebook</p>
            </button>
          </div>
        </section>

        <!-- Recent notebooks -->
        <section>
          <h2 class="text-lg font-medium text-text-primary mb-4">Recent notebooks</h2>

          @if (sessionService.loading()) {
            <div class="flex items-center justify-center py-12">
              <div class="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
            </div>
          } @else if (sessionService.sessions().length === 0) {
            <div class="text-center py-12 card">
              <svg class="w-16 h-16 mx-auto mb-4 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                  d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
              <h3 class="text-lg font-medium text-text-primary mb-2">No notebooks yet</h3>
              <p class="text-text-muted mb-4">Create your first notebook to get started</p>
              <button
                class="btn btn-primary"
                (click)="showCreateModal.set(true)"
              >
                Create notebook
              </button>
            </div>
          } @else {
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              @for (session of sessionService.sessions(); track session.id) {
                <div
                  class="card p-5 cursor-pointer hover:shadow-md transition-shadow group"
                  (click)="openSession(session)"
                >
                  <div class="flex items-start justify-between mb-3">
                    <div class="w-10 h-10 rounded-lg bg-primary-light flex items-center justify-center">
                      <svg class="w-5 h-5 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                      </svg>
                    </div>
                    <button
                      class="btn-icon p-1.5 opacity-0 group-hover:opacity-100 transition-opacity"
                      (click)="deleteSession(session, $event)"
                      aria-label="Delete notebook"
                    >
                      <svg class="w-4 h-4 text-text-muted hover:text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                          d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                      </svg>
                    </button>
                  </div>
                  <h3 class="font-medium text-text-primary mb-1 truncate">{{ session.title }}</h3>
                  <div class="flex items-center gap-3 text-sm text-text-muted">
                    <span class="flex items-center gap-1">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                      </svg>
                      {{ session.documentCount }} sources
                    </span>
                    <span>&bull;</span>
                    <span>{{ session.updatedAt | date:'mediumDate' }}</span>
                  </div>
                  <div class="mt-3">
                    <span class="badge" [class]="getModeClass(session.currentMode)">
                      {{ session.currentMode }}
                    </span>
                  </div>
                </div>
              }
            </div>
          }
        </section>
      </main>

      <!-- Create modal -->
      @if (showCreateModal()) {
        <div class="modal-overlay animate-fadeIn" (click)="showCreateModal.set(false)">
          <div class="modal-content p-6 animate-slideIn" (click)="$event.stopPropagation()">
            <h2 class="text-xl font-semibold text-text-primary mb-6">Create new notebook</h2>

            <div class="mb-6">
              <label class="block text-sm font-medium text-text-primary mb-2">
                Notebook title
              </label>
              <input
                type="text"
                class="input-field"
                placeholder="Enter a title for your notebook..."
                [value]="newTitle()"
                (input)="onTitleInput($event)"
                (keydown.enter)="createSession()"
              />
            </div>

            <div class="flex justify-end gap-3">
              <button
                class="btn btn-secondary"
                (click)="showCreateModal.set(false)"
              >
                Cancel
              </button>
              <button
                class="btn btn-primary"
                [disabled]="!newTitle().trim()"
                (click)="createSession()"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class SessionListComponent implements OnInit {
  private router = inject(Router);
  sessionService = inject(SessionService);

  showCreateModal = signal(false);
  newTitle = signal('');

  ngOnInit(): void {
    this.sessionService.loadSessions();
  }

  onTitleInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.newTitle.set(input.value);
  }

  createSession(): void {
    const title = this.newTitle().trim();
    if (!title) return;

    this.sessionService.createSession(title).subscribe(session => {
      this.showCreateModal.set(false);
      this.newTitle.set('');
      this.router.navigate(['/session', session.id]);
    });
  }

  openSession(session: Session): void {
    this.router.navigate(['/session', session.id]);
  }

  deleteSession(session: Session, event: Event): void {
    event.stopPropagation();
    if (confirm(`Delete "${session.title}"?`)) {
      this.sessionService.deleteSession(session.id).subscribe();
    }
  }

  getModeClass(mode: string): string {
    switch (mode) {
      case 'EXPLORING':
        return 'badge-primary';
      case 'RESEARCH':
        return 'badge-success';
      case 'LEARNING':
        return 'badge-warning';
      default:
        return 'badge-primary';
    }
  }
}

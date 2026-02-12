import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/session/components/session-list/session-list.component')
        .then(m => m.SessionListComponent),
    title: 'NotebookLM - Home'
  },
  {
    path: 'session/:id',
    loadComponent: () =>
      import('./features/session/components/session-layout/session-layout.component')
        .then(m => m.SessionLayoutComponent),
    title: 'NotebookLM - Session'
  },
  {
    path: '**',
    redirectTo: ''
  }
];

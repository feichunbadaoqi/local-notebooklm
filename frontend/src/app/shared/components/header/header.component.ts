import { Component, input, output } from '@angular/core';
import { InteractionMode } from '../../../core/models';
import { ModeSelectorComponent } from '../mode-selector/mode-selector.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [ModeSelectorComponent],
  host: {
    class: 'flex-shrink-0'
  },
  template: `
    <header class="h-14 px-4 flex items-center justify-between border-b border-border bg-bg-main">
      <!-- Left side - Logo and title -->
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2">
          <svg class="w-8 h-8 text-primary" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
          </svg>
          <span class="text-lg font-semibold text-text-primary">NotebookLM</span>
        </div>
        <span class="text-text-muted">/</span>
        <h1 class="text-base font-medium text-text-primary truncate max-w-md">
          {{ sessionTitle() }}
        </h1>
      </div>

      <!-- Center - Mode selector -->
      <div class="flex items-center">
        <app-mode-selector
          [currentMode]="currentMode()"
          (modeChange)="modeChange.emit($event)"
        />
      </div>

      <!-- Right side - Actions -->
      <div class="flex items-center gap-2">
        <button class="btn btn-secondary text-sm">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z"/>
          </svg>
          Share
        </button>
        <button class="btn-icon" aria-label="Settings">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
        </button>
      </div>
    </header>
  `
})
export class HeaderComponent {
  sessionTitle = input.required<string>();
  currentMode = input.required<InteractionMode>();
  modeChange = output<string>();
}

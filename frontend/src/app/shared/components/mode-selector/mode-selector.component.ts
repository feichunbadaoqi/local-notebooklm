import { Component, input, output, signal } from '@angular/core';
import { InteractionMode } from '../../../core/models';

interface ModeOption {
  value: InteractionMode;
  label: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-mode-selector',
  standalone: true,
  template: `
    <div class="relative">
      <button
        class="flex items-center gap-2 px-3 py-1.5 rounded-full border border-border hover:bg-bg-hover transition-colors"
        (click)="toggleDropdown()"
      >
        <span class="w-2 h-2 rounded-full" [class]="getModeColor(currentMode())"></span>
        <span class="text-sm font-medium text-text-primary">{{ getModeLabel(currentMode()) }}</span>
        <svg class="w-4 h-4 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
        </svg>
      </button>

      @if (showDropdown()) {
        <div
          class="absolute top-full mt-2 left-1/2 -translate-x-1/2 w-72 bg-bg-main border border-border rounded-xl shadow-lg z-50 animate-slideIn"
        >
          <div class="p-2">
            @for (mode of modes; track mode.value) {
              <button
                class="w-full flex items-start gap-3 p-3 rounded-lg hover:bg-bg-hover transition-colors text-left"
                [class.bg-bg-active]="mode.value === currentMode()"
                (click)="selectMode(mode.value)"
              >
                <span
                  class="w-2 h-2 rounded-full mt-1.5 flex-shrink-0"
                  [class]="getModeColor(mode.value)"
                ></span>
                <div>
                  <div class="font-medium text-text-primary">{{ mode.label }}</div>
                  <div class="text-sm text-text-muted">{{ mode.description }}</div>
                </div>
              </button>
            }
          </div>
        </div>
      }
    </div>

    <!-- Click outside to close -->
    @if (showDropdown()) {
      <div class="fixed inset-0 z-40" (click)="showDropdown.set(false)"></div>
    }
  `
})
export class ModeSelectorComponent {
  currentMode = input.required<InteractionMode>();
  modeChange = output<string>();

  showDropdown = signal(false);

  modes: ModeOption[] = [
    {
      value: 'EXPLORING',
      label: 'Exploring',
      description: 'Broad discovery with related suggestions',
      icon: 'compass'
    },
    {
      value: 'RESEARCH',
      label: 'Research',
      description: 'Precise citations and fact-focused answers',
      icon: 'search'
    },
    {
      value: 'LEARNING',
      label: 'Learning',
      description: 'Socratic method with explanations',
      icon: 'academic-cap'
    }
  ];

  toggleDropdown(): void {
    this.showDropdown.update(v => !v);
  }

  selectMode(mode: InteractionMode): void {
    this.modeChange.emit(mode);
    this.showDropdown.set(false);
  }

  getModeLabel(mode: InteractionMode): string {
    return this.modes.find(m => m.value === mode)?.label ?? mode;
  }

  getModeColor(mode: InteractionMode): string {
    switch (mode) {
      case 'EXPLORING':
        return 'bg-primary';
      case 'RESEARCH':
        return 'bg-success';
      case 'LEARNING':
        return 'bg-warning';
      default:
        return 'bg-secondary';
    }
  }
}

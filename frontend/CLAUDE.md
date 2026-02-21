# Frontend Standards (Angular/TypeScript)

**Tech:** Angular 20 (SSR) · RxJS · TypeScript · Tailwind CSS · SSE for chat streaming

## Component Design

- **Smart components (containers)**: Inject services, manage state, pass data down via `@Input`/`@Output`.
- **Presentational components (dumb)**: No service injection, pure `@Input`/`@Output`, use `ChangeDetectionStrategy.OnPush`.

## Service Design

- Define service interfaces for abstraction (e.g., `SessionService` interface + `SessionHttpService` implementation).
- Services return `Observable<T>` — never subscribe inside services.

## RxJS Rules

- Use `async` pipe in templates (auto-unsubscribe)
- Use `takeUntil(destroy$)` for manual subscriptions — clean up in `ngOnDestroy`
- Use `switchMap` over nested subscriptions
- Use `shareReplay(1)` for shared HTTP calls
- Use `debounceTime` + `distinctUntilChanged` for user input

## Naming

| Element | Pattern | Example |
|---------|---------|---------|
| Component | Feature + `Component` | `SessionListComponent` |
| Service | Feature + `Service` | `SessionService` |
| Interface | Noun (no "I" prefix) | `Session`, `ChatMessage` |
| Observable | `$` suffix | `session$`, `messages$` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_FILE_SIZE` |

## Directory Structure

```
src/app/
├── core/              # Singleton services, guards, interceptors
├── shared/            # Reusable components, pipes, directives
├── features/          # Feature modules (session/, chat/, etc.)
│   └── <feature>/
│       ├── components/
│       ├── services/
│       └── models/
└── app.component.ts
```

## Performance

- `OnPush` change detection for presentational components
- `trackBy` in all `*ngFor` loops
- Lazy load feature modules
- Debounce search/autocomplete inputs

## Testing

- Component tests: test behavior, mock services
- Service tests: `HttpClientTestingModule` + `HttpTestingController`
- Validate error states and loading states

## Quality Checks

```bash
npm run build          # TypeScript compilation check
npm test               # Karma/Jasmine tests
npm run lint:fix       # ESLint auto-fix
```

# Frontend Coding Standards (Angular/TypeScript)

**This file is automatically loaded by Claude Code when working in `frontend/`.**

## Quick Reference

- [Project Context](#project-context)
- [Component Design](#component-design)
- [Service Design](#service-design)
- [RxJS Best Practices](#rxjs-best-practices)
- [Dependency Injection](#dependency-injection)
- [Error Handling](#error-handling)
- [Naming Conventions](#naming-conventions)
- [Directory Structure](#directory-structure)
- [Testing](#testing)
- [Performance](#performance)
- [Security](#security)
- [npm Commands](#npm-commands)

## Project Context

**For shared project architecture, API endpoints, and SOLID principles, see [root CLAUDE.md](../CLAUDE.md).**

This file contains Angular/TypeScript specific coding standards for the NotebookLM frontend.

**Tech Stack:**
- Angular 20 with SSR
- RxJS for reactive programming
- TypeScript
- SSE (Server-Sent Events) for chat streaming

## Component Design

### Smart vs Presentational Components

**Smart components (containers):** Handle logic, state, and services.

```typescript
// ✅ CORRECT: Smart component (container)
@Component({
  selector: 'app-session-container',
  template: '<app-session-view [session]="session$ | async" (save)="onSave($event)"></app-session-view>'
})
export class SessionContainerComponent {
  session$ = this.sessionService.getSession(this.route.snapshot.params['id']);

  constructor(
    private sessionService: SessionService,
    private route: ActivatedRoute
  ) {}

  onSave(data: SessionData): void {
    this.sessionService.updateSession(data).subscribe();
  }
}
```

**Presentational components (dumb):** Pure, receive `@Input`, emit `@Output`.

```typescript
// ✅ CORRECT: Presentational component (dumb)
@Component({
  selector: 'app-session-view',
  template: '...'
})
export class SessionViewComponent {
  @Input() session: Session | null = null;
  @Output() save = new EventEmitter<SessionData>();

  // No service injection, no business logic
}
```

## Service Design

**Define service interfaces for better abstraction.**

```typescript
// ✅ CORRECT: Define service interface
export interface SessionService {
  getSession(id: string): Observable<Session>;
  createSession(request: CreateSessionRequest): Observable<Session>;
}

@Injectable({ providedIn: 'root' })
export class SessionHttpService implements SessionService {
  constructor(private http: HttpClient) {}

  getSession(id: string): Observable<Session> {
    return this.http.get<Session>(`/api/sessions/${id}`);
  }

  createSession(request: CreateSessionRequest): Observable<Session> {
    return this.http.post<Session>('/api/sessions', request);
  }
}
```

## RxJS Best Practices

- **Use `async` pipe in templates** (auto-unsubscribe)
- **Never subscribe in services** (return Observables)
- **Use `takeUntil`** for manual subscriptions in components
- **Prefer `switchMap`** over nested subscriptions
- **Use `shareReplay(1)`** for shared HTTP calls

### Example: Async Pipe

```typescript
// ✅ CORRECT: Use async pipe
@Component({
  template: '<div *ngIf="session$ | async as session">{{ session.title }}</div>'
})
export class SessionComponent {
  session$ = this.sessionService.getSession(this.route.snapshot.params['id']);

  constructor(
    private sessionService: SessionService,
    private route: ActivatedRoute
  ) {}
}
```

### Example: Manual Unsubscribe

```typescript
// ✅ CORRECT: Use takeUntil for manual subscriptions
@Component({ ... })
export class SessionComponent implements OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.sessionService.getSession(this.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(session => {
        // Handle session
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

### Example: SwitchMap

```typescript
// ✅ CORRECT: Use switchMap to avoid nested subscriptions
this.searchInput$.pipe(
  debounceTime(300),
  distinctUntilChanged(),
  switchMap(query => this.searchService.search(query))
).subscribe(results => {
  // Handle results
});

// ❌ WRONG: Nested subscriptions
this.searchInput$.subscribe(query => {
  this.searchService.search(query).subscribe(results => {
    // Handle results
  });
});
```

## Dependency Injection

### Constructor Injection

```typescript
// ✅ CORRECT: Constructor injection
@Component({ ... })
export class SessionComponent {
  constructor(
    private sessionService: SessionService,
    private router: Router,
    private route: ActivatedRoute
  ) {}
}
```

### Inject Function (Angular 14+)

```typescript
// ✅ CORRECT: inject() function (Angular 14+)
@Component({ ... })
export class SessionComponent {
  private sessionService = inject(SessionService);
  private router = inject(Router);
}
```

## Error Handling

### Centralized Error Handling Service

```typescript
// ✅ CORRECT: Centralized error handling
@Injectable({ providedIn: 'root' })
export class ErrorHandlerService {
  constructor(private snackBar: MatSnackBar) {}

  handle(error: HttpErrorResponse): void {
    const message = error.error?.message || 'An unexpected error occurred';
    this.snackBar.open(message, 'Close', { duration: 5000 });
    console.error('Error:', error);
  }
}

// Use in components
this.sessionService.createSession(request)
  .pipe(catchError(err => {
    this.errorHandler.handle(err);
    return EMPTY;
  }))
  .subscribe(session => { ... });
```

## Naming Conventions

| Element | Pattern | Example |
|---------|---------|---------|
| Component | Feature + "Component" | `SessionListComponent`, `ChatBoxComponent` |
| Service | Feature + "Service" | `SessionService`, `RagService` |
| Interface/Type | Noun (no "I" prefix) | `Session`, `ChatMessage`, `DocumentUpload` |
| Observable | Variable + "$" suffix | `session$`, `messages$`, `isLoading$` |
| Constants | UPPER_SNAKE_CASE | `MAX_FILE_SIZE`, `API_BASE_URL` |
| Private members | "_" prefix optional | `_destroyed$`, `_sessionId` |

## Directory Structure

```
src/app/
├── core/                  # Singleton services, guards, interceptors
│   ├── services/
│   ├── guards/
│   └── interceptors/
├── shared/                # Reusable components, pipes, directives
│   ├── components/
│   ├── pipes/
│   └── directives/
├── features/              # Feature modules
│   ├── session/
│   │   ├── components/    # Smart + presentational components
│   │   ├── services/      # Feature-specific services
│   │   ├── models/        # Interfaces, types
│   │   └── session.module.ts
│   └── chat/
└── app.component.ts
```

## Testing

### Component Testing

```typescript
// ✅ CORRECT: Test component behavior
it('should display error when session load fails', () => {
  const errorResponse = new HttpErrorResponse({ status: 404 });
  sessionService.getSession.mockReturnValue(throwError(() => errorResponse));

  fixture.detectChanges();

  expect(component.errorMessage).toBe('Session not found');
  expect(compiled.querySelector('.error')).toBeTruthy();
});
```

### Service Testing

```typescript
// ✅ CORRECT: Test service with HttpClientTestingModule
describe('SessionHttpService', () => {
  let service: SessionHttpService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SessionHttpService]
    });
    service = TestBed.inject(SessionHttpService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should fetch session by id', () => {
    const mockSession: Session = { id: '1', title: 'Test Session' };

    service.getSession('1').subscribe(session => {
      expect(session).toEqual(mockSession);
    });

    const req = httpMock.expectOne('/api/sessions/1');
    expect(req.request.method).toBe('GET');
    req.flush(mockSession);
  });

  afterEach(() => {
    httpMock.verify();
  });
});
```

## Performance

### Change Detection Optimization

```typescript
// ✅ CORRECT: Use OnPush for presentational components
@Component({
  selector: 'app-session-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '...'
})
export class SessionViewComponent {
  @Input() session: Session | null = null;
}
```

### TrackBy in *ngFor

```typescript
// ✅ CORRECT: Use trackBy function
@Component({
  template: `
    <div *ngFor="let session of sessions; trackBy: trackBySessionId">
      {{ session.title }}
    </div>
  `
})
export class SessionListComponent {
  sessions: Session[] = [];

  trackBySessionId(index: number, session: Session): string {
    return session.id;
  }
}
```

### Lazy Loading

```typescript
// ✅ CORRECT: Lazy load feature modules
const routes: Routes = [
  {
    path: 'sessions',
    loadChildren: () => import('./features/session/session.module').then(m => m.SessionModule)
  },
  {
    path: 'chat',
    loadChildren: () => import('./features/chat/chat.module').then(m => m.ChatModule)
  }
];
```

### Debounce User Input

```typescript
// ✅ CORRECT: Debounce search input
searchControl = new FormControl('');

ngOnInit(): void {
  this.searchControl.valueChanges.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    switchMap(query => this.searchService.search(query))
  ).subscribe(results => {
    this.results = results;
  });
}
```

### Key Performance Practices

- Use `OnPush` change detection for presentational components
- Lazy load feature modules with routing
- Use `trackBy` in `*ngFor` loops
- Unsubscribe from Observables (use `async` pipe or `takeUntil`)
- Debounce user input for search/autocomplete

## Security

- **Sanitize user-generated HTML** (Angular does this by default)
- **Never trust user input** in dynamic templates
- **Store sensitive tokens in memory**, not localStorage
- **Validate file types** before upload

### Example: File Validation

```typescript
// ✅ CORRECT: Validate file types before upload
onFileSelected(event: Event): void {
  const input = event.target as HTMLInputElement;
  if (!input.files?.length) return;

  const file = input.files[0];
  const allowedTypes = ['application/pdf', 'text/plain', 'application/msword'];

  if (!allowedTypes.includes(file.type)) {
    this.errorHandler.handle({ message: 'Invalid file type' });
    return;
  }

  this.uploadFile(file);
}
```

## Common Anti-Patterns to Avoid

| ❌ Anti-Pattern | ✅ Better Approach |
|----------------|-------------------|
| Subscribing in services | Return Observables, let components subscribe |
| Not unsubscribing | Use `async` pipe or `takeUntil` |
| Magic numbers/strings | Define named constants |
| Deep nesting (>3 levels) | Extract methods, use guard clauses |
| Copy-paste code | Extract shared logic to utilities/services |
| Returning `null` | Use `null` with type guards or `undefined` |
| No trackBy in *ngFor | Add trackBy function for better performance |

## Documentation

**When to Add Comments:**
- Complex algorithms or business logic
- Non-obvious workarounds or hacks
- Public API methods (use TSDoc)

**When NOT to Add Comments:**
- Self-explanatory code (prefer better naming)
- Redundant descriptions

```typescript
// ✅ CORRECT: Explains "why", not "what"
// Use RRF (Reciprocal Rank Fusion) to combine vector and BM25 scores
// Formula: score(d) = Σ 1/(k + rank_i) where k=60 (constant)
const combinedScore = calculateRRFScore(vectorRank, bm25Rank);

// ❌ WRONG: States obvious
// Get the session by ID
const session = this.sessionService.getSession(id);
```

## npm Commands

### Development

```bash
npm start                      # Dev server at localhost:4200
npm run build                  # Production build
npm test                       # Run Karma/Jasmine tests
npm run serve:ssr:frontend     # Run SSR production server
```

### Code Quality

```bash
npm run lint                   # Run ESLint
npm run lint:fix               # Fix linting issues automatically
```

### After Every Code Change

- Ensure no TypeScript errors (`npm run build`)
- Run tests (`npm test`)
- Fix linting issues (`npm run lint:fix`)

# MemoryService Design Document

## Overview

The MemoryService extracts and manages **long-term knowledge** from conversations that persists across chat sessions. Unlike chat history (which gets compacted), memories are distilled facts, preferences, and insights that remain accessible.

## Memory Types

| Type | Description | Example |
|------|-------------|---------|
| `fact` | Factual information from documents | "The project deadline is March 15th" |
| `preference` | User preferences or working style | "User prefers bullet-point summaries" |
| `insight` | Connections or conclusions drawn | "Revenue correlates with marketing spend in Q3" |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         ChatServiceImpl                          │
│                                                                  │
│  streamChat() ──► onCompleteResponse() ──► memoryService        │
│                                              .extractAndSave()   │
└──────────────────────────────────────────────┬──────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                         MemoryService                            │
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌──────────────┐│
│  │ extractMemories │───►│ deduplicateWith │───►│    save      ││
│  │   (LLM call)    │    │    existing     │    │              ││
│  └─────────────────┘    └─────────────────┘    └──────────────┘│
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │ getRelevant     │───►│ buildMemory     │ ◄── used by Chat   │
│  │   Memories      │    │   Context       │     for prompts    │
│  └─────────────────┘    └─────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

## Files Structure

```
service/memory/
├── MemoryService.java           # Interface
└── MemoryServiceImpl.java       # Implementation

api/rest/
└── MemoryController.java        # REST endpoints

api/dto/
├── request/
│   └── CreateMemoryRequest.java # Manual memory creation
└── response/
    └── MemoryResponse.java      # Memory data response
```

## MemoryService Interface

```java
public interface MemoryService {

    /**
     * Extracts memories from a conversation exchange and saves them.
     * Called asynchronously after each chat response.
     *
     * @param sessionId the session
     * @param userMessage the user's message
     * @param assistantResponse the assistant's response
     * @param mode the interaction mode (affects extraction style)
     */
    void extractAndSaveAsync(UUID sessionId, String userMessage,
                             String assistantResponse, InteractionMode mode);

    /**
     * Gets relevant memories for the current query.
     * Used to enrich the conversation context.
     *
     * @param sessionId the session
     * @param query the current user query
     * @param limit max memories to return
     * @return list of relevant memories
     */
    List<Memory> getRelevantMemories(UUID sessionId, String query, int limit);

    /**
     * Builds a formatted string of memories for the system prompt.
     *
     * @param memories the memories to format
     * @return formatted memory context string
     */
    String buildMemoryContext(List<Memory> memories);

    /**
     * Gets all memories for a session.
     */
    List<Memory> getAllMemories(UUID sessionId);

    /**
     * Deletes a specific memory.
     */
    void deleteMemory(UUID memoryId);

    /**
     * Manually adds a memory (user-defined).
     */
    Memory addMemory(UUID sessionId, String content, String type, Float importance);
}
```

## Memory Extraction Prompt

```
You are a memory extraction assistant. Analyze the following conversation exchange
and extract important facts, user preferences, or insights worth remembering.

User message: {userMessage}
Assistant response: {assistantResponse}

Extract memories in JSON format:
[
  {"type": "fact|preference|insight", "content": "...", "importance": 0.0-1.0}
]

Rules:
- Only extract genuinely important information worth remembering long-term
- Facts: specific data points, dates, names, numbers from documents
- Preferences: how the user likes information presented or what they focus on
- Insights: connections, conclusions, or patterns discovered
- Importance: 0.0 (trivial) to 1.0 (critical)
- Return empty array [] if nothing worth remembering
- Keep each memory concise (1-2 sentences max)
```

## Integration with ChatServiceImpl

### Memory Extraction (after response)

```java
// In ChatServiceImpl.streamChat() -> onCompleteResponse()

@Override
public void onCompleteResponse(ChatResponse completeResponse) {
    // ... existing code ...

    // Save assistant message
    String fullResponse = responseBuilder.get().toString();
    saveMessage(session, MessageRole.ASSISTANT, fullResponse, mode);

    // Extract memories asynchronously (non-blocking)
    memoryService.extractAndSaveAsync(sessionId, userMessage, fullResponse, mode);

    // ... rest of existing code ...
}
```

### Memory Retrieval (before generation)

```java
private List<ChatMessage> buildConversationContext(...) {
    List<ChatMessage> messages = new ArrayList<>();

    // System prompt
    String systemPrompt = buildSystemPrompt(mode, ragContext);
    messages.add(SystemMessage.from(systemPrompt));

    // Add relevant memories
    List<Memory> memories = memoryService.getRelevantMemories(
        session.getId(), currentMessage, memoryConfig.getContextLimit());
    if (!memories.isEmpty()) {
        String memoryContext = memoryService.buildMemoryContext(memories);
        messages.add(SystemMessage.from(memoryContext));
    }

    // Chat summary (existing)
    // Recent messages (existing)
    // Current user message (existing)

    return messages;
}
```

## Memory Context Format

```
Relevant memories from this session:
- [FACT] The project deadline is March 15th (importance: 0.9)
- [PREFERENCE] User prefers concise bullet-point summaries (importance: 0.7)
- [INSIGHT] Q3 revenue increase correlates with marketing campaign launch (importance: 0.8)

Use these memories to provide contextually aware responses.
```

## Deduplication Strategy

Before saving new memories, check for duplicates:

1. **Exact match** on `memoryContent` → skip
2. **High similarity** (normalized Levenshtein distance > 0.8) → merge and increase importance
3. **Otherwise** → save as new memory

Future enhancement: Use embedding similarity for semantic deduplication.

## Configuration

```yaml
rag:
  memory:
    enabled: true
    max-per-session: 50          # Max memories per session
    extraction-threshold: 0.3    # Min importance to save
    context-limit: 5             # Max memories in context
```

## REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/sessions/{sessionId}/memories` | List all memories for session |
| POST | `/api/sessions/{sessionId}/memories` | Manually create a memory |
| GET | `/api/sessions/{sessionId}/memories/{memoryId}` | Get specific memory |
| DELETE | `/api/sessions/{sessionId}/memories/{memoryId}` | Delete a memory |

### Request/Response Examples

#### GET /api/sessions/{sessionId}/memories

Response:
```json
[
  {
    "id": "uuid",
    "sessionId": "uuid",
    "memoryContent": "The project deadline is March 15th",
    "memoryType": "fact",
    "importance": 0.9,
    "createdAt": "2026-02-11T10:00:00",
    "lastAccessedAt": "2026-02-11T12:30:00"
  }
]
```

#### POST /api/sessions/{sessionId}/memories

Request:
```json
{
  "content": "User prefers detailed explanations",
  "type": "preference",
  "importance": 0.7
}
```

Response:
```json
{
  "id": "uuid",
  "sessionId": "uuid",
  "memoryContent": "User prefers detailed explanations",
  "memoryType": "preference",
  "importance": 0.7,
  "createdAt": "2026-02-11T14:00:00",
  "lastAccessedAt": "2026-02-11T14:00:00"
}
```

## Error Handling

- `SessionNotFoundException` - if session doesn't exist
- `MemoryNotFoundException` - if memory doesn't exist (new exception)
- Circuit breaker on LLM calls for memory extraction
- Graceful degradation: if extraction fails, log and continue (don't block chat)

## Metrics

| Metric | Description |
|--------|-------------|
| `memory.extraction.count` | Number of extraction attempts |
| `memory.extraction.success` | Successful extractions |
| `memory.extraction.errors` | Failed extractions |
| `memory.saved.count` | Memories saved |
| `memory.retrieved.count` | Memories retrieved for context |

---

*Created: 2026-02-11*

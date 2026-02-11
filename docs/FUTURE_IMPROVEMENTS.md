# Future Improvements

This document tracks planned enhancements that are deferred for future implementation.

---

## Phase 4: LangGraph4j Agent Orchestration

**Status:** Deferred
**Priority:** Medium
**Complexity:** High

### Overview

Replace the current linear chat flow in `ChatServiceImpl` with a LangGraph4j state machine for more sophisticated agent behaviors.

### Current Implementation

The current `ChatServiceImpl.streamChat()` handles everything sequentially:
```
User Query → Hybrid Search → Build Context → LLM Generation → Save → Compaction Check
```

This works well for simple RAG chat but lacks:
- Conditional branching (e.g., skip search if no documents)
- Multi-step reasoning with tool calls
- Autonomous decision-making by the agent

### Proposed Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    LangGraph4j Agent Flow                    │
│                                                              │
│  START                                                       │
│    │                                                         │
│    ▼                                                         │
│  ┌─────────────┐    no docs    ┌─────────────────┐          │
│  │ CheckDocs   │ ─────────────→│ AskClarification │          │
│  └─────────────┘               └─────────────────┘          │
│    │ has docs                          │                     │
│    ▼                                   │                     │
│  ┌─────────────┐                       │                     │
│  │ Retrieval   │                       │                     │
│  │    Node     │                       │                     │
│  └─────────────┘                       │                     │
│    │                                   │                     │
│    ▼                                   │                     │
│  ┌─────────────┐                       │                     │
│  │ Generation  │ ←─────────────────────┘                     │
│  │    Node     │                                             │
│  └─────────────┘                                             │
│    │                                                         │
│    ▼                                                         │
│  ┌─────────────┐                                             │
│  │MemoryUpdate │                                             │
│  │    Node     │                                             │
│  └─────────────┘                                             │
│    │                                                         │
│    ▼                                                         │
│  ┌─────────────┐                                             │
│  │ Compaction  │                                             │
│  │   Check     │                                             │
│  └─────────────┘                                             │
│    │                                                         │
│    ▼                                                         │
│   END                                                        │
└─────────────────────────────────────────────────────────────┘
```

### Files to Create

```
com.flamingo.ai.notebooklm/
├── agent/
│   ├── AgentState.java              # Typed state container
│   ├── AgentOrchestrator.java       # LangGraph4j graph definition
│   ├── nodes/
│   │   ├── CheckDocsNode.java       # Verify session has documents
│   │   ├── RetrievalNode.java       # Execute hybrid search
│   │   ├── GenerationNode.java      # LLM response generation
│   │   ├── MemoryUpdateNode.java    # Extract and store memories
│   │   └── CompactionCheckNode.java # Trigger history compaction
│   └── modes/
│       ├── ExploringAgent.java      # Broad discovery behavior
│       ├── ResearchAgent.java       # Citation-focused behavior
│       └── LearningAgent.java       # Socratic method behavior
```

### AgentState Design

```java
@Data
@Builder
public class AgentState {
    private UUID sessionId;
    private String userQuery;
    private InteractionMode mode;

    // Retrieval results
    private List<DocumentChunk> retrievedChunks;
    private String ragContext;

    // Conversation context
    private List<ChatMessage> conversationHistory;
    private List<ChatSummary> summaries;

    // Generation results
    private String generatedResponse;
    private List<Citation> citations;

    // Memory extraction
    private List<Memory> extractedMemories;

    // Control flow
    private boolean hasDocuments;
    private boolean needsClarification;
    private String clarificationQuestion;
}
```

### When to Implement

Implement Phase 4 when you need:

1. **Tool Calling** - Agent decides to use web search, calculator, or code execution
2. **Autonomous Research** - Agent performs multi-step investigation without user input
3. **Clarification Requests** - Agent asks follow-up questions before answering
4. **Multi-Agent Collaboration** - Specialized agents (researcher, fact-checker, editor)
5. **Complex Conditional Logic** - Different paths based on query type or context

### Dependencies

```gradle
// Add to build.gradle when implementing
implementation 'org.langgraph4j:langgraph4j-core:1.0.0'
```

### References

- [LangGraph4j Documentation](https://github.com/langchain4j/langgraph4j)
- [LangGraph Python (original)](https://langchain-ai.github.io/langgraph/)
- [Agent Architectures](https://docs.langchain4j.dev/tutorials/agents/)

---

## Other Future Enhancements

### Web Page Ingestion
- Parse URLs and extract content (similar to NotebookLM's web source support)
- Use JSoup or similar for HTML parsing
- Handle JavaScript-rendered pages with headless browser

### YouTube Transcript Support
- Extract transcripts from YouTube videos
- Parse VTT/SRT subtitle formats
- Chunk by timestamp for citation accuracy

### Audio Generation (Podcast Mode)
- Generate podcast-style audio discussions
- Use text-to-speech API (OpenAI TTS, ElevenLabs)
- Two-voice dialogue format

### Collaborative Sessions
- Multi-user sessions with shared documents
- Real-time updates via WebSocket
- User-specific chat history within shared context

### Export Features
- Export chat as Markdown/PDF
- Export document summaries
- Export extracted memories/facts

---

*Last Updated: 2026-02-11*

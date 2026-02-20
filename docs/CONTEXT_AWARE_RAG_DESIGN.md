# Context-Aware RAG for Follow-up Queries

## Problem Statement

When a user asks Q1 and gets response R1, then asks Q2 as a follow-up based on R1, the RAG retrieves unrelated chunks alongside relevant ones. The system does not understand the user is staying within R1's topic scope.

## Root Causes Identified

### 1. Reformulation Context May Miss the Immediately Preceding Turn
`QueryReformulationServiceImpl.reformulate()` uses semantic search (`ChatHistoryHybridSearchService`) to find the `historyWindow=5` most semantically similar past messages. For vague follow-ups ("Can you elaborate?", "What about that?"), semantic search may rank the immediately preceding exchange LOW because the vague query does not match it textually or semantically.

### 2. No Source Anchoring for Follow-up Queries
`HybridSearchService.searchWithDetails()` searches ALL document chunks with no knowledge of which documents R1 cited. Unrelated chunks from other documents rank equally alongside relevant ones.

### 3. `retrievedContextJson` Is Never Populated
`ChatMessage.retrievedContextJson` exists to store which document chunks backed an assistant response, but `saveMessage()` never sets it. The lineage of "what was retrieved for this response" is lost, making follow-up anchoring impossible.

### 4. No Follow-up Signal from Reformulation
`QueryReformulationResult` only returns `needsReformulation + query + reasoning`. There is no `isFollowUp` flag to tell downstream search to narrow its scope to the previous response documents.

---

## Solution Design

### Overview

Three capabilities working together:
1. **Recency-biased reformulation context** — always include the last Q&A turn regardless of semantic search
2. **Follow-up detection** — agent signals when query specifically continues the previous response topic
3. **Source anchoring** — boost document chunks from the previous response in RRF scoring

---

## New / Modified Files

| File | Action | Purpose |
|------|--------|---------|
| `service/rag/ReformulatedQuery.java` | **CREATE** | Richer return type from reformulation service |
| `agent/dto/QueryReformulationResult.java` | **MODIFY** | Add `isFollowUp` boolean field |
| `agent/QueryReformulationAgent.java` | **MODIFY** | Add `recentExchange` param; update prompts |
| `service/rag/QueryReformulationService.java` | **MODIFY** | Change return type to `ReformulatedQuery` |
| `service/rag/QueryReformulationServiceImpl.java` | **MODIFY** | Recency merge, anchor ID extraction |
| `service/rag/HybridSearchService.java` | **MODIFY** | Source anchoring boost in RRF |
| `service/chat/ChatServiceImpl.java` | **MODIFY** | Use new types, save `retrievedContextJson` |
| `config/RagConfig.java` | **MODIFY** | New `minRecentMessages`, `sourceAnchoringEnabled`, `sourceAnchoringBoost` fields |
| `src/main/resources/application.yaml` | **MODIFY** | Add default values for new config |

---

## Component Design

### `ReformulatedQuery` record
```java
public record ReformulatedQuery(
    String query,
    boolean isFollowUp,
    List<String> anchorDocumentIds
) {}
```
- `anchorDocumentIds`: Document IDs from previous assistant response (parsed from `retrievedContextJson`). Empty when not a follow-up or no prior context.

### `QueryReformulationResult` (modified)
```java
public record QueryReformulationResult(
    boolean needsReformulation,
    boolean isFollowUp,
    String query,
    String reasoning) {}
```
- `isFollowUp=true`: Query specifically continues what the immediately preceding assistant response discussed.

### `QueryReformulationAgent` (modified)
- New `{{recentExchange}}` template variable: last Q&A turn always explicitly visible to the agent
- Updated system prompt teaches agent to distinguish `isFollowUp=true` (continues immediately preceding response) vs `isFollowUp=false` (standalone or earlier context)

### `QueryReformulationServiceImpl` (modified)
Key logic:
1. Fetch last `minRecentMessages=2` from DB directly (last USER + ASSISTANT, always)
2. Fetch semantically relevant messages via hybrid search
3. Merge: recent DB messages first, fill remainder up to `historyWindow` with semantic results (deduplicate by ID)
4. Format `recentExchange` from last 2 DB messages
5. Parse last ASSISTANT message's `retrievedContextJson` → `anchorDocumentIds`
6. Call agent with `recentExchange`, `conversationHistory`, `query`
7. Return `ReformulatedQuery{query, isFollowUp, anchorDocumentIds}`

### `HybridSearchService` (modified)
New overloaded method:
```java
public SearchResult searchWithDetails(
    UUID sessionId, String query, InteractionMode mode, List<String> anchorDocumentIds)
```
After standard RRF scoring, applies additive boost to chunks from anchor documents:
```
for each chunk in rrfScores:
    if chunk.documentId.toString() in anchorDocumentIds:
        rrfScores[chunk.id] += sourceAnchoringBoost  // default 0.3
```
Existing `searchWithDetails(UUID, String, InteractionMode)` delegates to new overload with empty list — fully backward compatible.

### `ChatServiceImpl` (modified)
1. Use `ReformulatedQuery` return type from `queryReformulationService.reformulate()`
2. Apply source anchoring when `isFollowUp=true`
3. In `onCompleteResponse`, serialize `relevantChunks` document IDs → JSON and save with ASSISTANT message

### `RagConfig` additions
```yaml
rag:
  query-reformulation:
    min-recent-messages: 2    # Always fetch these from DB
  retrieval:
    source-anchoring-enabled: true
    source-anchoring-boost: 0.3
```

---

## Data Flow

```
User asks Q2 (follow-up to R1)
  |
  v
ChatServiceImpl.streamChat()
  1. Save USER message
  |
  v
QueryReformulationServiceImpl.reformulate()
  2. Fetch last 2 messages from DB (last USER + ASSISTANT with retrievedContextJson)
  3. Fetch up to historyWindow=5 semantically relevant messages
  4. Merge: recent DB messages first, fill with semantic (deduplicated)
  5. Parse last ASSISTANT's retrievedContextJson → anchorDocumentIds=[docA, docB]
  6. Call QueryReformulationAgent(recentExchange, conversationHistory, query)
  7. Agent returns: {needsReformulation=true, isFollowUp=true, query="expanded Q2"}
  8. Return ReformulatedQuery{query, isFollowUp=true, anchorDocumentIds=[docA, docB]}
  |
  v
ChatServiceImpl continues
  9. isFollowUp=true → HybridSearchService.searchWithDetails(..., anchorDocumentIds=[docA, docB])
     - Standard vector + BM25 + RRF for all chunks
     - RRF scores for chunks from docA/docB get +0.3 additive boost
     - Result: chunks from R1's documents rank higher
 10. Confidence check
 11. Build conversation context
 12. Stream LLM response
 13. onCompleteResponse:
     - Serialize relevantChunks doc IDs → JSON array
     - Save ASSISTANT message WITH retrievedContextJson
 14. Memory extraction, compaction check
```

---

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `rag.query-reformulation.min-recent-messages` | `2` | Messages to always fetch from DB |
| `rag.retrieval.source-anchoring-enabled` | `true` | Enable follow-up document boosting |
| `rag.retrieval.source-anchoring-boost` | `0.3` | Additive RRF score boost for anchor docs |

### Tuning Notes
- `sourceAnchoringBoost=0.3` is conservative; too high = fails to recognize genuine topic shifts
- `minRecentMessages=2` ensures last Q&A turn always reaches the reformulation agent

---

## Verification Plan

### Unit Tests

1. **`QueryReformulationServiceImplTest`**:
   - Last 2 messages from DB always included even when semantic search returns none
   - `anchorDocumentIds` extracted from previous ASSISTANT message's `retrievedContextJson`
   - `isFollowUp=true` propagates from agent response to `ReformulatedQuery`

2. **`HybridSearchServiceTest`**:
   - Anchor document chunks receive configured score boost after RRF
   - Empty `anchorDocumentIds` produces identical results to no-anchoring (backward compat)

3. **`ChatServiceImplTest`**:
   - `retrievedContextJson` populated in saved ASSISTANT messages
   - `searchWithDetails(... anchorDocumentIds)` called when `isFollowUp=true`
   - Normal search called when `isFollowUp=false` or anchoring disabled

### Manual E2E Test

1. Upload a document with two distinct topics (Topic A in sections 1-3, Topic B in sections 4-6)
2. Ask Q1 about Topic A → get R1 (should cite sections 1-3)
3. Ask Q2: "Can you elaborate more on that?" (vague follow-up)
4. Verify: retrieved chunks are from sections 1-3, NOT sections 4-6
5. Ask Q3: "Now tell me about Topic B" (explicit topic shift)
6. Verify: retrieved chunks switch to sections 4-6 (`isFollowUp` should be false)

# Multi-Document RAG Optimization Design

## Overview

This document describes the architecture and implementation plan for optimizing RAG (Retrieval-Augmented Generation) to support multiple documents in a single chat session with high accuracy.

## Goals

1. **Multi-Document Support**: Effectively search and retrieve relevant content across all documents in a session
2. **Improved Accuracy**: Better matching through metadata enrichment and hierarchical search
3. **Result Diversity**: Ensure results come from multiple relevant documents, not just one
4. **Proper Attribution**: Clear citations showing which document each piece of information comes from

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Query                                │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│           Level 1: Document-Level Search                         │
│  • Search document summaries/embeddings                          │
│  • Return top-N relevant documents (e.g., top 5 of 50 docs)     │
│  • Skip if session has ≤ 3 documents                            │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│           Level 2: Chunk-Level Search                            │
│  • Hybrid search: Vector + BM25 + RRF fusion                    │
│  • Filter by documentIds from Level 1 (if applicable)           │
│  • Retrieve top-K×2 candidates for reranking                    │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│           Level 3: Diversity-Aware Reranking                     │
│  • Score by relevance                                            │
│  • Round-robin selection across documents                        │
│  • Ensure minimum representation from each relevant document     │
│  • Return final top-K chunks                                     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Context Assembly                              │
│  • Format chunks with source attribution                         │
│  • Include document titles and section info                      │
│  • Build prompt with citation instructions                       │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    LLM Generation                                │
│  • Generate response grounded in retrieved context              │
│  • Include inline citations [1], [2], etc.                      │
│  • Stream response via SSE                                       │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Metadata Extractor

**Purpose**: Extract structured metadata from documents during ingestion.

**Extracted Fields**:
- `title`: Document title (from first heading or filename)
- `sections`: List of section/chapter headers
- `keywords`: Important terms extracted via TF-IDF
- `summary`: LLM-generated summary (optional, for hierarchical search)

**Location**: `service/rag/DocumentMetadataExtractor.java`

```java
public class DocumentMetadata {
    private String title;
    private List<String> sections;
    private List<String> keywords;
    private String summary;           // Optional: LLM-generated
    private List<Float> summaryEmbedding;  // Optional: for doc-level search
}
```

### 2. Enhanced Document Chunk

**Purpose**: Store enriched chunks with metadata for better retrieval.

**Elasticsearch Index Schema**:
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "documentId": { "type": "keyword" },
      "sessionId": { "type": "keyword" },
      "fileName": { "type": "text" },
      "documentTitle": { "type": "text", "analyzer": "standard" },
      "sectionTitle": { "type": "text", "analyzer": "standard" },
      "keywords": { "type": "text", "analyzer": "standard" },
      "chunkIndex": { "type": "integer" },
      "content": { "type": "text", "analyzer": "standard" },
      "enrichedContent": { "type": "text", "analyzer": "standard" },
      "embedding": {
        "type": "dense_vector",
        "dims": 3072,
        "index": true,
        "similarity": "cosine"
      },
      "tokenCount": { "type": "integer" }
    }
  }
}
```

**Enriched Content Format**:
```
[Document: Q4 Financial Report 2024]
[Section: Revenue Analysis]
[Keywords: revenue, growth, quarterly, projections]

The company achieved record revenue of $1.2B in Q4, representing
a 25% year-over-year growth...
```

### 3. Document Summary (Optional - Phase 2)

**Purpose**: Enable document-level search for sessions with many documents.

**Database Table**: `document_summaries`
```sql
CREATE TABLE document_summaries (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    session_id UUID NOT NULL REFERENCES sessions(id),
    title VARCHAR(500),
    summary TEXT,
    keywords TEXT,
    topics TEXT,
    summary_embedding TEXT,  -- JSON array of floats
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id)
);
```

**Entity**: `domain/entity/DocumentSummary.java`

### 4. Hierarchical Search Service

**Purpose**: Two-level search - documents first, then chunks.

**Location**: `service/rag/HierarchicalSearchService.java`

**Algorithm**:
```
function hierarchicalSearch(sessionId, query, topK):
    documentCount = countDocuments(sessionId)

    if documentCount <= 3:
        // Skip document-level search for small sessions
        return hybridSearch(sessionId, query, topK)

    // Level 1: Find relevant documents
    relevantDocIds = documentLevelSearch(sessionId, query, top=5)

    if relevantDocIds.isEmpty():
        // Fallback to searching all documents
        return hybridSearch(sessionId, query, topK)

    // Level 2: Search within relevant documents
    candidates = hybridSearchWithFilter(sessionId, query, relevantDocIds, topK * 2)

    // Level 3: Rerank with diversity
    return diversityRerank(candidates, topK, minPerDoc=2)
```

### 5. Diversity Reranker

**Purpose**: Ensure search results include content from multiple documents.

**Location**: `service/rag/DiversityReranker.java`

**Algorithm**:
```
function diversityRerank(chunks, topK, minPerDoc):
    // Group chunks by document
    byDocument = groupBy(chunks, chunk -> chunk.documentId)

    result = []
    round = 0

    // Round-robin selection
    while result.size < topK and byDocument.notEmpty():
        for each (docId, docChunks) in byDocument:
            if round < docChunks.size:
                result.add(docChunks[round])
                if result.size >= topK:
                    break
            else if round >= minPerDoc:
                byDocument.remove(docId)
        round++

    return result
```

**Example**:
```
Input (sorted by relevance):
  Doc A: [chunk1, chunk3, chunk5, chunk7]  (4 chunks)
  Doc B: [chunk2, chunk4]                   (2 chunks)
  Doc C: [chunk6]                           (1 chunk)

Round-robin with minPerDoc=2, topK=6:
  Round 0: A.chunk1, B.chunk2, C.chunk6
  Round 1: A.chunk3, B.chunk4
  Round 2: A.chunk5 (C exhausted after min met)

Output: [A.chunk1, B.chunk2, C.chunk6, A.chunk3, B.chunk4, A.chunk5]
```

## Implementation Phases

### Phase 1: Metadata-Enriched Chunks (Priority: High)

**Files to Create**:
- `service/rag/DocumentMetadataExtractor.java`

**Files to Modify**:
- `elasticsearch/DocumentChunk.java` - Add metadata fields
- `elasticsearch/ElasticsearchIndexService.java` - Update index mapping
- `service/rag/DocumentProcessingService.java` - Extract and attach metadata

**Implementation Steps**:
1. Create `DocumentMetadataExtractor` with methods:
   - `extractTitle(content, fileName)` - Get title from headings or filename
   - `extractSections(content)` - Find section headers
   - `extractKeywords(content, topN)` - TF-IDF keyword extraction

2. Update `DocumentChunk` model:
   ```java
   @Builder.Default
   private String documentTitle = "";
   private String sectionTitle;
   private List<String> keywords;
   private String enrichedContent;  // Metadata + content combined
   ```

3. Update chunking logic to:
   - Track current section while chunking
   - Attach section title to each chunk
   - Generate enrichedContent with metadata prefix

4. Update Elasticsearch index mapping to include new fields

5. Embed `enrichedContent` instead of raw `content`

**Testing**:
- Unit tests for metadata extraction
- Integration test verifying enriched chunks are indexed
- Search accuracy comparison before/after

### Phase 2: Diversity Reranking (Priority: High)

**Files to Create**:
- `service/rag/DiversityReranker.java`

**Files to Modify**:
- `service/rag/HybridSearchService.java` - Integrate reranker

**Implementation Steps**:
1. Create `DiversityReranker` service
2. Add configuration for:
   - `minChunksPerDocument` (default: 2)
   - `enabled` (default: true)
3. Integrate into `HybridSearchService.search()` method
4. Add metrics for diversity (documents represented in results)

**Testing**:
- Unit tests for reranking algorithm
- Integration test with multi-document session

### Phase 3: Hierarchical Search (Priority: Medium)

**Files to Create**:
- `domain/entity/DocumentSummary.java`
- `domain/repository/DocumentSummaryRepository.java`
- `service/rag/HierarchicalSearchService.java`
- `service/rag/DocumentSummarizationService.java`

**Files to Modify**:
- `service/rag/DocumentProcessingService.java` - Generate summaries
- `service/chat/ChatServiceImpl.java` - Use hierarchical search

**Implementation Steps**:
1. Create `DocumentSummary` entity and repository
2. Create `DocumentSummarizationService`:
   - Generate summary using LLM (optional, can use first N chunks)
   - Extract main topics
   - Generate summary embedding
3. Create `HierarchicalSearchService`:
   - Document-level search using summary embeddings
   - Chunk-level search with document filter
   - Integration with diversity reranker
4. Add configuration:
   - `hierarchicalSearchThreshold` (default: 3 documents)
   - `topDocuments` (default: 5)

**Testing**:
- Integration test with 10+ documents
- Performance benchmark
- Accuracy comparison

## Configuration

**application.yaml additions**:
```yaml
app:
  rag:
    metadata:
      extract-keywords: true
      max-keywords: 10
      extract-sections: true
    diversity:
      enabled: true
      min-chunks-per-document: 2
    hierarchical:
      enabled: true
      document-threshold: 3      # Use hierarchical if > N documents
      top-documents: 5           # Max documents to search in level 2
    retrieval:
      top-k: 8                   # Final chunks to return
      candidates-multiplier: 2  # Retrieve topK * N for reranking
```

## Context Assembly Format

When passing retrieved chunks to the LLM, format as:

```
You are answering questions based on the user's uploaded documents.
Use ONLY the information from the sources below. Cite sources using [1], [2], etc.

=== SOURCES ===

[1] Financial Report 2024 (financial-report.pdf) - Section: Revenue Analysis
The company achieved record revenue of $1.2B in Q4...

[2] Product Roadmap (roadmap.md) - Section: Q1 2025 Plans
Key features planned for Q1 include...

[3] Financial Report 2024 (financial-report.pdf) - Section: Projections
Based on current trends, we project 30% growth...

=== END SOURCES ===

User Question: {query}
```

## Metrics & Observability

**New Metrics**:
- `rag.metadata.extraction.duration` - Time to extract metadata
- `rag.search.documents.found` - Number of documents in results
- `rag.search.diversity.score` - Ratio of documents represented
- `rag.hierarchical.level1.duration` - Document-level search time
- `rag.hierarchical.level2.duration` - Chunk-level search time
- `rag.rerank.duration` - Reranking time

**Logging**:
```
INFO  - Hierarchical search: found 3 relevant docs from 15 total
DEBUG - Level 1 results: [doc1: 0.89, doc2: 0.76, doc3: 0.71]
DEBUG - Level 2 retrieved 16 chunks, reranked to 8
INFO  - Final results: 3 chunks from doc1, 3 from doc2, 2 from doc3
```

## Migration Plan

### For Existing Sessions

1. **Metadata Backfill** (Optional):
   - Create migration job to re-process existing documents
   - Extract metadata and update Elasticsearch index
   - Can be done lazily on next document access

2. **Index Compatibility**:
   - New fields are optional (nullable)
   - Existing chunks work without metadata (degraded matching)
   - New documents get full metadata enrichment

### Rollout Strategy

1. Deploy Phase 1 (metadata enrichment) behind feature flag
2. A/B test search accuracy
3. Enable for all users after validation
4. Deploy Phase 2 (diversity) - low risk, enable immediately
5. Deploy Phase 3 (hierarchical) behind feature flag
6. Enable hierarchical for sessions with 5+ documents

## Success Criteria

| Metric | Target |
|--------|--------|
| Search relevance (manual eval) | +20% improvement |
| Multi-doc query accuracy | 80%+ correct citations |
| Document diversity in results | ≥2 docs per query (when applicable) |
| Search latency (P95) | <500ms |
| Indexing latency | <10% increase |

## Appendix: Keyword Extraction Algorithm

Simple TF-IDF implementation for keyword extraction:

```java
public List<String> extractKeywords(String content, int topN) {
    // Tokenize and clean
    List<String> tokens = tokenize(content.toLowerCase());
    tokens = removeStopwords(tokens);

    // Calculate term frequency
    Map<String, Integer> tf = new HashMap<>();
    for (String token : tokens) {
        tf.merge(token, 1, Integer::sum);
    }

    // Simple IDF approximation (penalize very common words)
    // In production, use corpus-wide IDF
    Map<String, Double> scores = new HashMap<>();
    int totalTokens = tokens.size();

    for (Map.Entry<String, Integer> entry : tf.entrySet()) {
        String term = entry.getKey();
        int freq = entry.getValue();

        // TF: log-normalized
        double tfScore = 1 + Math.log(freq);

        // Simple length bonus for multi-word terms (if using n-grams)
        double lengthBonus = term.length() > 6 ? 1.2 : 1.0;

        scores.put(term, tfScore * lengthBonus);
    }

    // Return top N
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topN)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
}
```

## References

- [Hybrid Search with RRF](https://www.elastic.co/blog/hybrid-search-elasticsearch)
- [Hypothetical Document Embeddings (HyDE)](https://arxiv.org/abs/2212.10496)
- [Lost in the Middle: How Language Models Use Long Contexts](https://arxiv.org/abs/2307.03172)
- [Sentence Transformers: Semantic Search](https://www.sbert.net/examples/applications/semantic-search/README.html)

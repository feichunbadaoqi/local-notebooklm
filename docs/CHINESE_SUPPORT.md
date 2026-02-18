# Chinese Language Support Guide

This guide explains how Chinese (and other CJK languages) are supported in NotebookLM.

## Overview

The system supports multilingual documents including Chinese, Japanese, and Korean (CJK). Two main components handle this:

1. **Token Estimation** - Conservative estimate for CJK character density
2. **Elasticsearch Text Analysis** - IK Analysis plugin for proper Chinese word segmentation

## Problem with Default Configuration

The default `standard` analyzer in Elasticsearch:
- ✅ Works for English (splits on whitespace and punctuation)
- ❌ **Fails for Chinese** — no spaces between words, so it tokenizes character-by-character
- ❌ Poor BM25 keyword search results for Chinese queries

## Solution: IK Analysis Plugin

### What is IK Analysis?

IK Analysis (`elasticsearch-analysis-ik`) is the most widely adopted open-source Chinese analyzer for Elasticsearch. It provides two analyzers:

| Analyzer | Use Case | Behavior |
|----------|----------|----------|
| `ik_max_word` | **Index time** | Maximum granularity — generates every possible token |
| `ik_smart` | **Search time** | Smart segmentation — fewer, more meaningful tokens |

### Why `ik_max_word` for indexing + `ik_smart` for search?

Using different analyzers at index and search time is the recommended IK pattern:

- **Indexing with `ik_max_word`** ensures all possible tokens are in the index (high recall). Example: "腾讯云原生" is indexed as both "腾讯云" and "腾讯" and "云原生".
- **Searching with `ik_smart`** produces fewer, more precise tokens per query (high precision). The same phrase becomes "腾讯云" + "原生", matching the most relevant documents without over-splitting.

### Why IK Over SmartCN?

| Feature | SmartCN | IK Analysis |
|---------|---------|-------------|
| Word segmentation accuracy | Good | Better |
| Community adoption | Official Elastic plugin | Most popular in China |
| Custom dictionaries | ❌ No | ✅ Yes |
| Index/search analyzer split | ❌ Single mode | ✅ `ik_max_word` + `ik_smart` |
| Mixed Chinese/English | ✅ Yes | ✅ Yes |

## Configuration

The project is already configured for IK Analysis. Settings in `application.yaml`:

```yaml
app:
  elasticsearch:
    text-analyzer: ik_max_word        # Used for indexing
    text-search-analyzer: ik_smart    # Used for search queries
```

These apply to all text fields across the three indexes:
- `notebooklm-chunks` — `content`, `documentTitle`, `sectionTitle`, `enrichedContent`
- `notebooklm-chat-messages` — `content`
- `notebooklm-memories` — `memoryContent`

## Quick Start (Docker — Recommended)

The project's `Dockerfile.elasticsearch` already installs the IK plugin. Just rebuild:

```bash
cd backend

# Stop existing containers
docker-compose down

# Remove old Elasticsearch data (required — analyzer change needs fresh index)
docker volume rm backend_elasticsearch-data

# Build custom Elasticsearch image with IK plugin
docker-compose build

# Start services
docker-compose up -d

# Wait for Elasticsearch to be ready (~30-60 seconds)
# Verify IK plugin is installed
curl http://localhost:9200/_cat/plugins
# Expected: analysis-ik

# Start the backend application
./gradlew bootRun
```

## Manual Installation (Non-Docker)

```bash
# Match your Elasticsearch version
bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/9.1.4

# Restart Elasticsearch, then recreate the index
curl -X DELETE "http://localhost:9200/notebooklm-chunks"
curl -X DELETE "http://localhost:9200/notebooklm-chat-messages"
curl -X DELETE "http://localhost:9200/notebooklm-memories"

# Restart the backend — it will recreate indexes with the new analyzer
./gradlew bootRun
```

## How IK Handles Text

### Chinese Text

```
Input:  "人工智能是未来的技术"

ik_max_word (index): ["人工智能", "人工", "智能", "是", "未来", "的", "技术"]
ik_smart    (search): ["人工智能", "是", "未来", "的", "技术"]
```

### English Text

```
Input:  "Artificial Intelligence is the future"
Both:   ["artificial", "intelligence", "is", "the", "future"]
```

### Mixed Chinese + English (common in tech documents)

```
Input:  "AI人工智能和Machine Learning机器学习"

ik_max_word (index): ["ai", "人工智能", "人工", "智能", "和", "machine", "learning", "机器学习", "机器", "学习"]
ik_smart    (search): ["ai", "人工智能", "和", "machine", "learning", "机器学习"]
```

### Why This Matters for Search

**Without IK (standard analyzer):**
- Query: "人工智能"
- Indexed as: `["人", "工", "智", "能"]` (character-by-character)
- Result: ❌ Poor matches or no results

**With IK:**
- Indexed as: `["人工智能", "人工", "智能"]` (meaningful tokens)
- Searched as: `["人工智能"]`
- Result: ✅ Accurate matches

## Verification

Test the IK analyzers after setup:

```bash
# Test ik_max_word (indexing — fine-grained)
curl -X POST "http://localhost:9200/notebooklm-chunks/_analyze" \
  -H 'Content-Type: application/json' -d'{
  "analyzer": "ik_max_word",
  "text": "腾讯云原生最佳实践"
}'
# Expected tokens: ["腾讯云", "腾讯", "云", "原生", "最佳", "实践"]

# Test ik_smart (search — coarser)
curl -X POST "http://localhost:9200/notebooklm-chunks/_analyze" \
  -H 'Content-Type: application/json' -d'{
  "analyzer": "ik_smart",
  "text": "腾讯云原生最佳实践"
}'
# Expected tokens: ["腾讯云", "原生", "最佳", "实践"]
```

## Token Estimation

The code uses a conservative token estimate for CJK:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `CHARS_PER_TOKEN_ESTIMATE` | 1.8 | CJK chars are denser than English |
| `MAX_TOKENS_PER_EMBEDDING` | ~6,000 | Safety margin under 8,192 limit |

**Files:** `EmbeddingService.java`, `DocumentProcessingService.java`

## Troubleshooting

### "Index has wrong analyzer" / poor search results after config change

You must delete and recreate all indexes. Changing the analyzer config does **not** update existing indexes.

```bash
curl -X DELETE "http://localhost:9200/notebooklm-chunks"
curl -X DELETE "http://localhost:9200/notebooklm-chat-messages"
curl -X DELETE "http://localhost:9200/notebooklm-memories"
# Restart the backend to recreate them
```

### "Plugin not found"

Ensure the plugin version matches your Elasticsearch version exactly:

```bash
curl http://localhost:9200  # Check ES version
# Then install the matching plugin version
```

### Still getting token limit errors

Reduce limits further in `EmbeddingService.java`:
```java
private static final int MAX_TOKENS_PER_EMBEDDING = 5000;
private static final double CHARS_PER_TOKEN_ESTIMATE = 1.5;
```

## References

- [IK Analysis Plugin (GitHub)](https://github.com/medcl/elasticsearch-analysis-ik)
- [Infini Cloud IK Builds](https://get.infini.cloud/elasticsearch/analysis-ik/)
- [OpenAI Embedding Limits](https://platform.openai.com/docs/guides/embeddings)

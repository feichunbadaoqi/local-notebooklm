# Chinese Language Support Guide

This guide explains how to enable proper Chinese (and other CJK languages) support in NotebookLM.

## Overview

The system now supports multilingual documents, including Chinese, Japanese, and Korean (CJK) languages. Two main components needed updating:

1. **Token Estimation** - Fixed to handle CJK character density
2. **Elasticsearch Text Analysis** - Configurable analyzer for proper Chinese word segmentation

## Problem with Default Configuration

The default `standard` analyzer in Elasticsearch:
- ✅ Works great for English (splits on spaces and punctuation)
- ❌ **Fails for Chinese** - No spaces between words, so it tokenizes character-by-character
- ❌ Poor keyword search (BM25) results for Chinese queries

## Solution: SmartCN Analyzer

### What is SmartCN?

SmartCN (Smart Chinese Analysis) is Elasticsearch's **official built-in** Chinese analyzer that:
- ✅ Segments Chinese text into meaningful words (e.g., "中国" stays as one word, not "中" + "国")
- ✅ **Handles BOTH Chinese AND English** in the same document
- ✅ Works well for mixed content like "AI人工智能技术" → ["ai", "人工智能", "技术"]
- ✅ Maintained by Elastic (official plugin)
- ✅ Already configured in this project via Docker

**Official Documentation:** https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-smartcn

## Quick Start (Docker - Recommended)

The project is **already configured** for Chinese support! Just rebuild and restart:

```powershell
# Navigate to backend directory
cd backend

# Stop existing containers
docker-compose down

# Remove old Elasticsearch data (IMPORTANT - will delete indexed documents!)
docker volume rm backend_elasticsearch-data

# Build custom Elasticsearch image with SmartCN plugin
docker-compose build

# Start services
docker-compose up -d

# Wait for Elasticsearch to be ready (30-60 seconds)
Start-Sleep -Seconds 45

# Verify SmartCN plugin is installed
curl http://localhost:9200/_cat/plugins

# Start the backend application
./gradlew bootRun
```

**That's it!** The system now supports both Chinese and English.

## Manual Installation (Non-Docker)

If you're NOT using Docker, install SmartCN manually:

<details>
<summary>Click to expand manual installation instructions</summary>

**On Windows:**
```powershell
cd C:\path\to\elasticsearch-8.12.0
.\bin\elasticsearch-plugin.bat install analysis-smartcn
# Restart Elasticsearch
```

**On Linux/Mac:**
```bash
cd /path/to/elasticsearch-8.12.0
./bin/elasticsearch-plugin install analysis-smartcn
sudo systemctl restart elasticsearch
```

Then configure in `application.yaml`:
```yaml
app:
  elasticsearch:
    text-analyzer: smartcn
```

And recreate the index:
```bash
curl -X DELETE "http://localhost:9200/notebooklm-chunks"
```
</details>

## How SmartCN Handles Mixed Languages

SmartCN is specifically designed for **bilingual Chinese/English content**. Here's how it works:

### Chinese Text
```bash
Input:  "人工智能是未来的技术"
Tokens: ["人工智能", "是", "未来", "的", "技术"]
```

### English Text
```bash
Input:  "Artificial Intelligence is the future technology"
Tokens: ["artificial", "intelligence", "is", "the", "future", "technology"]
```

### Mixed Chinese + English (Common in tech documents)
```bash
Input:  "AI人工智能和Machine Learning机器学习"
Tokens: ["ai", "人工智能", "和", "machine", "learning", "机器学习"]
```

### Why This Matters for Search

**Without SmartCN (standard analyzer):**
- Query: "人工智能" (AI)
- Indexed as: ["人", "工", "智", "能"] (character-by-character)
- Result: ❌ Poor matches or no results

**With SmartCN:**
- Query: "人工智能" (AI)
- Indexed as: ["人工智能"] (meaningful word)
- Result: ✅ Accurate matches

### Verification

Test that the SmartCN analyzer is working:

```bash
# Test Chinese text analysis
curl -X POST "http://localhost:9200/notebooklm-chunks/_analyze" -H 'Content-Type: application/json' -d'
{
  "analyzer": "smartcn",
  "text": "中国是一个伟大的国家"
}
'

# Should return tokens like: ["中国", "是", "一个", "伟大", "的", "国家"]
# NOT: ["中", "国", "是", "一", "个", "伟", "大", "的", "国", "家"]
```

## Alternative Analyzers

### IK Analysis (Advanced)

For more advanced Chinese word segmentation, consider IK Analysis:

**Features:**
- More accurate Chinese word segmentation
- Two modes: `ik_smart` (coarse) and `ik_max_word` (fine)
- Customizable dictionaries

**Installation:**
```bash
# Download from https://github.com/medcl/elasticsearch-analysis-ik/releases
# Match your Elasticsearch version (e.g., 8.12.2)
elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.12.2/elasticsearch-analysis-ik-8.12.2.zip

# Configure in application.yaml:
# text-analyzer: ik_smart  # OR ik_max_word
```

### Comparison

| Analyzer | Best For | Installation | Configuration |
|----------|----------|--------------|---------------|
| `standard` | English only | Built-in | Default |
| `smartcn` | Mixed Chinese/English | One command | Recommended |
| `ik_smart` | Chinese-focused | Manual download | Advanced |
| `ik_max_word` | Chinese-focused, fine-grained | Manual download | Advanced |

## Token Estimation Fix

The code now uses a conservative token estimate that works for all languages:

**Before (English-optimized):**
- 2.5 chars/token
- 18,750 char limit
- ❌ Fails on Chinese (10,354 tokens → exceeds 8,192 limit)

**After (Multilingual):**
- 1.8 chars/token (conservative for CJK)
- 10,800 char limit
- ✅ Works for Chinese, English, and all other languages
- ~6,000 token target with safety margin

**Files Changed:**
- `EmbeddingService.java:22-28` - Token estimation constants
- `DocumentProcessingService.java:280` - Chunk size limit

## Testing

### Test with Chinese Document

1. Install SmartCN and configure as above
2. Restart backend
3. Upload a Chinese document (e.g., `.docx`, `.pdf`)
4. Check logs for successful processing:
```
Document xxx split into 215 chunks
Successfully indexed 215 chunks to Elasticsearch
```

### Test Search

1. Upload a Chinese document about "人工智能" (AI)
2. Ask: "人工智能是什么？" (What is AI?)
3. Should return relevant chunks from the document

## Troubleshooting

### Issue: "Index has wrong analyzer"

**Symptom:** Search results are poor even after installing SmartCN

**Solution:** You must delete and recreate the index (see step 3 above). Changing the analyzer in the config won't update existing indexes.

### Issue: "Plugin not found"

**Symptom:** Elasticsearch fails to start after plugin installation

**Solution:** Ensure plugin version matches your Elasticsearch version exactly:
```bash
# Check ES version
curl http://localhost:9200

# Download matching plugin version
```

### Issue: "Still getting token limit errors"

**Symptom:** Embeddings still fail with "maximum context length" error

**Solution:** The token estimate might still be too high. Reduce further in `EmbeddingService.java`:
```java
private static final int MAX_TOKENS_PER_EMBEDDING = 5000; // Even more conservative
private static final double CHARS_PER_TOKEN_ESTIMATE = 1.5; // For very dense CJK text
```

## Performance Considerations

### SmartCN vs Standard

- **Indexing Speed:** SmartCN is ~10-20% slower (word segmentation overhead)
- **Search Accuracy:** SmartCN is MUCH better for Chinese (50-80% improvement)
- **Index Size:** Similar (no significant difference)
- **English Support:** SmartCN works fine for English too

### Recommendations

- ✅ Use `smartcn` if you have ANY Chinese documents
- ✅ Use `ik_smart` if Chinese is your primary language
- ✅ Use `standard` only if you have zero CJK content

## References

- [Elasticsearch SmartCN Plugin](https://www.elastic.co/guide/en/elasticsearch/plugins/current/analysis-smartcn.html)
- [IK Analysis Plugin](https://github.com/medcl/elasticsearch-analysis-ik)
- [OpenAI Embedding Limits](https://platform.openai.com/docs/guides/embeddings)

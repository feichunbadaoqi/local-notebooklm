# Restart Guide: Chinese Support with All Fixes

All Chinese support issues have been fixed! Here's what changed and how to restart.

## What Was Fixed?

### 1. ✅ Token Limit Errors (Embedding)
- **Before:** 2.5 chars/token → Chinese documents exceeded 8,192 token limit
- **After:** 1.8 chars/token → Conservative estimate for CJK languages
- **Impact:** No more "maximum context length" errors

### 2. ✅ Console Display (????)
- **Before:** Chinese characters displayed as "????" in logs
- **After:** Added UTF-8 encoding to JVM and bootRun
- **Impact:** Chinese file names and titles display correctly (if your terminal supports UTF-8)

### 3. ✅ Keyword Extraction (Only English)
- **Before:** Only extracted English keywords: `[comment, 2024, esg...]`
- **After:** Extracts both Chinese AND English keywords
- **Technical:** Changed tokenizer from `[^a-zA-Z0-9']+` to `[^\\p{L}\\p{N}']+` (Unicode support)
- **Impact:** Chinese keywords like "人工智能", "技术" will now appear

### 4. ✅ Elasticsearch Chinese Search
- **Before:** Standard analyzer → character-by-character tokenization
- **After:** SmartCN analyzer → proper Chinese word segmentation
- **Impact:** Accurate search results for Chinese queries

## How to Restart and Test

### Step 1: Rebuild Elasticsearch with SmartCN

```powershell
cd E:\workspaces\aicoderpg\notebooklm\backend

# Stop containers and remove old data
docker-compose down
docker volume rm backend_elasticsearch-data

# Build Elasticsearch with SmartCN plugin
docker-compose build

# Start services
docker-compose up -d

# Wait for Elasticsearch to initialize (45 seconds)
Start-Sleep -Seconds 45

# Verify plugin installed
curl http://localhost:9200/_cat/plugins
# Expected: notebooklm-elasticsearch analysis-smartcn 8.12.0
```

### Step 2: Set PowerShell to UTF-8 (For Correct Display)

```powershell
# Set console to UTF-8 for this session
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

# OR add to your PowerShell profile permanently:
# notepad $PROFILE
# Add these lines:
#   [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
#   $env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
```

### Step 3: Start Backend with UTF-8

```powershell
# Still in backend directory
./gradlew bootRun

# You should now see Chinese characters properly in logs (if terminal supports UTF-8)
# Example: "Uploading document 宝马2024年度报告.docx" instead of "????2024????????.docx"
```

### Step 4: Test with Your Chinese Document

1. Open frontend: http://localhost:4200
2. Upload: `宝马2024年度报告.docx` (or your Chinese document)
3. Wait for processing to complete
4. Check backend logs for:
   - ✅ Document title showing Chinese characters (or at least not "????")
   - ✅ Keywords including Chinese words (not just English)
   - ✅ "Successfully indexed X chunks"
5. Ask a question in Chinese: "人工智能是什么？" or "ESG是什么？"
6. Verify relevant results appear

## Expected Log Output (Fixed)

### Before (Broken):
```
Uploading document ????2024????????.docx
Extracted metadata - title: 2024?, keywords: [comment, 2024, esg, ehs, 2023]
```

### After (Fixed):
```
Uploading document 宝马2024年度报告.docx
Extracted metadata - title: 宝马2024年度报告, keywords: [人工智能, 技术, esg, ehs, 2024]
```

**Note:** If you still see "????" in Windows Terminal/PowerShell, that's just a display limitation of your terminal. The data is stored correctly and search will work. Try using Windows Terminal instead of Command Prompt for better UTF-8 support.

## Verify Everything Works

### Test 1: Plugin Installed
```powershell
curl http://localhost:9200/_cat/plugins
```
✅ Should show: `analysis-smartcn 8.12.0`

### Test 2: SmartCN Analyzer Works
```powershell
curl -X POST "http://localhost:9200/notebooklm-chunks/_analyze" -H "Content-Type: application/json" -d "{\"analyzer\": \"smartcn\", \"text\": \"人工智能技术\"}"
```
✅ Should return tokens: `["人工智能", "技术"]` (not character-by-character)

### Test 3: Upload & Search
1. Upload Chinese document
2. Check logs for Chinese keywords (not just English)
3. Search in Chinese
4. Get relevant results

## Troubleshooting

### Still See "????" in Logs

This is **just a display issue**. If your terminal doesn't support UTF-8:

**Windows Terminal (Recommended):**
```powershell
# Install Windows Terminal from Microsoft Store
# It has better UTF-8 support than PowerShell
```

**Verify Data is Correct:**
```powershell
# Query database directly to see real data
sqlite3 data/notebooklm.db "SELECT fileName FROM documents LIMIT 5;"
```

### Chinese Keywords Still Not Appearing

1. **Did you rebuild the backend?** Changes require restart:
   ```powershell
   ./gradlew clean build
   ./gradlew bootRun
   ```

2. **Check if document has Chinese content:**
   - Keywords are extracted from document content, not just filename
   - If document is mostly English with Chinese title, keywords will be mostly English

### Search Not Working for Chinese

1. **Did you rebuild Elasticsearch?** SmartCN requires rebuilding the index
2. **Did you upload documents AFTER recreating the index?** Old documents need re-uploading

## Summary of Files Changed

1. `backend/gradle.properties` - Added UTF-8 JVM args
2. `backend/build.gradle` - Added bootRun UTF-8 config
3. `backend/Dockerfile.elasticsearch` - Install SmartCN plugin
4. `backend/compose.yaml` - Build custom ES image
5. `application.yaml` - Changed `text-analyzer: smartcn`
6. `EmbeddingService.java` - Fixed token estimation (1.8 chars/token)
7. `DocumentMetadataExtractor.java` - Fixed tokenizer for Unicode (Chinese)
8. `DocumentProcessingService.java` - Reduced chunk size for safety

## Need Help?

- Full documentation: `docs/CHINESE_SUPPORT.md`
- Quick start: `QUICKSTART_CHINESE.md`
- Check logs for errors: `./gradlew bootRun > logs.txt 2>&1`

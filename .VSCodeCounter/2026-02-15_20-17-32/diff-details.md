# Diff Details

Date : 2026-02-15 20:17:32

Directory e:\\workspaces\\aicoderpg\\notebooklm

Total : 55 files,  3833 codes, 585 comments, 1021 blanks, all 5439 lines

[Summary](results.md) / [Details](details.md) / [Diff Summary](diff.md) / Diff Details

## Files
| filename | language | code | comment | blank | total |
| :--- | :--- | ---: | ---: | ---: | ---: |
| [.claude/settings.local.json](/.claude/settings.local.json) | JSON | 12 | 0 | 0 | 12 |
| [CLAUDE.md](/CLAUDE.md) | Markdown | 13 | 0 | 18 | 31 |
| [backend/CLAUDE.md](/backend/CLAUDE.md) | Markdown | 418 | 0 | 123 | 541 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/MemoryExtractionAgent.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/MemoryExtractionAgent.java) | Java | 32 | 4 | 7 | 43 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/QueryReformulationAgent.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/QueryReformulationAgent.java) | Java | 39 | 4 | 10 | 53 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/ExtractedMemory.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/ExtractedMemory.java) | Java | 6 | 4 | 2 | 12 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/QueryReformulationResult.java](/backend/src/main/java/com/flamingo/ai/notebooklm/agent/dto/QueryReformulationResult.java) | Java | 6 | 4 | 2 | 12 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionWithStats.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SessionWithStats.java) | Java | 18 | 2 | 4 | 24 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SystemStats.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/dto/response/SystemStats.java) | Java | 16 | 1 | 3 | 20 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/HealthController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/HealthController.java) | Java | -2 | 0 | 0 | -2 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/MemoryController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/MemoryController.java) | Java | -5 | -2 | -4 | -11 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/SessionController.java](/backend/src/main/java/com/flamingo/ai/notebooklm/api/rest/SessionController.java) | Java | -14 | 0 | 0 | -14 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/AiAgentConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/AiAgentConfig.java) | Java | 18 | 14 | 5 | 37 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/LangChain4jConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/LangChain4jConfig.java) | Java | 1 | 0 | 0 | 1 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/config/RagConfig.java](/backend/src/main/java/com/flamingo/ai/notebooklm/config/RagConfig.java) | Java | 8 | 0 | 1 | 9 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunk.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunk.java) | Java | 2 | 1 | 2 | 5 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java) | Java | 88 | 45 | 19 | 152 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexOperations.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexOperations.java) | Java | 12 | 47 | 10 | 69 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/ElasticsearchIndexService.java) | Java | 24 | 19 | 2 | 45 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/ApiError.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/ApiError.java) | Java | 1 | 0 | 0 | 1 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/GlobalExceptionHandler.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/GlobalExceptionHandler.java) | Java | 20 | 0 | 3 | 23 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryAccessDeniedException.java](/backend/src/main/java/com/flamingo/ai/notebooklm/exception/MemoryAccessDeniedException.java) | Java | 19 | 1 | 7 | 27 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatCompactionService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatCompactionService.java) | Java | -4 | 0 | -2 | -6 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImpl.java) | Java | 31 | 3 | 4 | 38 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthService.java) | Java | 5 | 6 | 4 | 15 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/health/HealthServiceImpl.java) | Java | 33 | 1 | 6 | 40 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryService.java) | Java | 1 | 9 | 1 | 11 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImpl.java) | Java | -48 | -3 | -10 | -61 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationService.java) | Java | 135 | 44 | 34 | 213 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java) | Java | 122 | 37 | 33 | 192 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingService.java) | Java | 25 | 9 | 3 | 37 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingService.java) | Java | 19 | 31 | 11 | 61 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java) | Java | 36 | 13 | 5 | 54 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationService.java) | Java | 6 | 12 | 3 | 21 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImpl.java) | Java | 92 | 7 | 21 | 120 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/RetrievalConfidenceService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/RetrievalConfidenceService.java) | Java | 163 | 42 | 45 | 250 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionService.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionService.java) | Java | 3 | 11 | 2 | 16 |
| [backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImpl.java](/backend/src/main/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImpl.java) | Java | 33 | 0 | 4 | 37 |
| [backend/src/main/resources/application.yaml](/backend/src/main/resources/application.yaml) | YAML | 7 | 0 | 0 | 7 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/api/sse/ChatControllerIntegrationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/api/sse/ChatControllerIntegrationTest.java) | Java | 226 | 2 | 49 | 277 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/chat/ChatServiceImplTest.java) | Java | 52 | 6 | 13 | 71 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/memory/MemoryServiceImplTest.java) | Java | -5 | 0 | 0 | -5 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/AnswerVerificationServiceTest.java) | Java | 233 | 7 | 72 | 312 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java) | Java | 178 | 13 | 53 | 244 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractorTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentMetadataExtractorTest.java) | Java | 341 | 2 | 120 | 463 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/DocumentProcessingServiceTest.java) | Java | 171 | 43 | 51 | 265 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/EmbeddingServiceTest.java) | Java | 208 | 2 | 62 | 272 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchServiceTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchServiceTest.java) | Java | 34 | 6 | 0 | 40 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/QueryReformulationServiceImplTest.java) | Java | 194 | 32 | 59 | 285 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagEvaluationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagEvaluationTest.java) | Java | 150 | 61 | 47 | 258 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagMetrics.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/RagMetrics.java) | Java | 82 | 44 | 17 | 143 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/SessionIsolationIntegrationTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/SessionIsolationIntegrationTest.java) | Java | 20 | 1 | 2 | 23 |
| [backend/src/test/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImplTest.java](/backend/src/test/java/com/flamingo/ai/notebooklm/service/session/SessionServiceImplTest.java) | Java | 6 | 0 | 2 | 8 |
| [backend/src/test/resources/rag-test-set.json](/backend/src/test/resources/rag-test-set.json) | JSON | 181 | 0 | 1 | 182 |
| [frontend/CLAUDE.md](/frontend/CLAUDE.md) | Markdown | 371 | 0 | 95 | 466 |

[Summary](results.md) / [Details](details.md) / [Diff Summary](diff.md) / Diff Details